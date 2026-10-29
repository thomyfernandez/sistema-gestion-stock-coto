package ar.com.coto.stock.persistence;

import ar.com.coto.stock.domain.model.*;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Ejecuta el SQL real sobre el mismo motor H2 que utiliza el perfil demo. */
class SchemaTest {
    private String url;

    @BeforeEach void prepararBase() throws Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection conexion = DriverManager.getConnection(url)) {
            for (String archivo : new String[]{"/schema.sql", "/data.sql"}) {
                try (var lector = new InputStreamReader(Objects.requireNonNull(
                        getClass().getResourceAsStream(archivo)), StandardCharsets.UTF_8)) {
                    RunScript.execute(conexion, lector);
                }
            }
        }
    }

    @Test void cargaDatosYReconstruyeCompositeConDisponibilidadEsperada() throws Exception {
        try (Connection conexion = DriverManager.getConnection(url)) {
            Map<Integer, Stock> stocks = reconstruir(conexion);
            assertEquals(6, stocks.size());
            assertEquals(80, stocks.get(1).getDisponible());
            assertEquals(35, stocks.get(2).getDisponible());
            assertEquals(6, stocks.get(3).getDisponible());
            assertEquals(16, stocks.get(4).getDisponible());
            assertEquals(30, stocks.get(5).getDisponible());
            assertEquals(35, stocks.get(6).getDisponible());
            stocks.get(6).reservar(2);
            assertEquals(76, stocks.get(1).getDisponible());
            assertEquals(33, stocks.get(2).getDisponible());
            assertEquals(33, stocks.get(6).getDisponible());
        }
    }

    @Test void movimientosReconstruyenExactamenteLosContadoresIniciales() throws Exception {
        try (Connection conexion = DriverManager.getConnection(url); Statement consulta = conexion.createStatement()) {
            Map<Integer, Stock> stocks = reconstruir(conexion);
            Map<Integer, StockSimple> reconstruidos = new HashMap<>();
            stocks.forEach((id, stock) -> {
                if (stock instanceof StockSimple) reconstruidos.put(id, new StockSimple(stock.getProducto(), 0, 0, 0));
            });
            try (ResultSet movimientos = consulta.executeQuery("SELECT * FROM movimiento_stock ORDER BY fecha, id")) {
                while (movimientos.next()) {
                    int id = movimientos.getInt("stock_id");
                    reconstruidos.get(id).aplicarMovimiento(new MovimientoStock(id,
                            TipoMovimiento.valueOf(movimientos.getString("tipo")), movimientos.getInt("cantidad"),
                            movimientos.getObject("fecha", java.time.OffsetDateTime.class).toInstant(),
                            movimientos.getString("motivo"), movimientos.getString("usuario")));
                }
            }
            reconstruidos.forEach((id, stock) -> {
                StockSimple esperado = (StockSimple) stocks.get(id);
                assertEquals(esperado.getStockFisico(), stock.getStockFisico());
                assertEquals(esperado.getStockExhibicion(), stock.getStockExhibicion());
                assertEquals(esperado.getStockReservado(), stock.getStockReservado());
            });
        }
    }

    @Test void esquemaNoTieneDisponibleNiSucursalNiTablasSeparadasDeSubtipos() throws Exception {
        try (Connection conexion = DriverManager.getConnection(url)) {
            DatabaseMetaData metadata = conexion.getMetaData();
            try (ResultSet columnas = metadata.getColumns(null, "public", "stock", "%")) {
                while (columnas.next()) assertNotEquals("disponible", columnas.getString("COLUMN_NAME"));
            }
            for (String tabla : new String[]{"sucursal", "stock_simple", "combo_stock"}) {
                try (ResultSet tablas = metadata.getTables(null, "public", tabla, new String[]{"TABLE"})) {
                    assertFalse(tablas.next(), "Tabla prohibida: " + tabla);
                }
            }
        }
    }

    @Test void restriccionesProtegenContadoresTiposYComposicion() throws Exception {
        try (Connection conexion = DriverManager.getConnection(url); Statement sentencia = conexion.createStatement()) {
            for (String sql : new String[]{
                    "UPDATE stock SET stock_reservado = 500 WHERE id = 1",
                    "UPDATE stock SET stock_fisico = -1 WHERE id = 1",
                    "UPDATE stock SET stock_fisico = NULL WHERE id = 1",
                    "UPDATE stock SET stock_fisico = 0 WHERE id = 6",
                    "INSERT INTO linea_combo(combo_id, componente_id, cantidad_en_combo) VALUES(1, 2, 1)",
                    "INSERT INTO linea_combo(combo_id, componente_id, cantidad_en_combo) VALUES(6, 6, 1)",
                    "INSERT INTO linea_combo(combo_id, componente_id, cantidad_en_combo) VALUES(6, 3, 0)",
                    "INSERT INTO linea_combo(combo_id, componente_id, cantidad_en_combo) VALUES(6, 999, 1)",
                    "INSERT INTO pedido_reposicion(stock_id, cantidad) VALUES(6, 10)",
                    "INSERT INTO pedido_reposicion(stock_id, cantidad) VALUES(1, 0)"}) {
                assertThrows(SQLException.class, () -> sentencia.executeUpdate(sql), sql);
            }
        }
    }

    @Test void identidadesGeneradasNoColisionanConFixtures() throws Exception {
        try (Connection conexion = DriverManager.getConnection(url); Statement sentencia = conexion.createStatement()) {
            sentencia.executeUpdate("INSERT INTO producto(nombre, descripcion, precio) VALUES('Nuevo', 'Prueba', 100)");
            try (ResultSet resultado = sentencia.executeQuery("SELECT id FROM producto WHERE nombre = 'Nuevo'")) {
                assertTrue(resultado.next());
                assertTrue(resultado.getInt(1) >= 1000);
            }
        }
    }

    @Test void recepcionValidaTrasCerrarLaConexionQueCreoLosChecks() throws Exception {
        // prepararBase ya cerró la conexión del DDL. La nueva sesión debe poder
        // evaluar CHECK(tipo IN (...)) aunque se haya retirado la sesión original.
        try (Connection conexion = DriverManager.getConnection(url);
             PreparedStatement insertar = conexion.prepareStatement(
                     "INSERT INTO movimiento_stock(stock_id,tipo,cantidad,motivo,usuario) VALUES(3,?,30,?,?)")) {
            insertar.setString(1, "RECEPCION");
            insertar.setString(2, "Recepción del pedido 1");
            insertar.setString(3, "recepcion.demo");
            assertEquals(1, insertar.executeUpdate());

            // Actualizar el motor no debe desactivar la validación SQL.
            insertar.setString(1, "TIPO_INEXISTENTE");
            SQLException error = assertThrows(SQLException.class, insertar::executeUpdate);
            assertEquals("23513", error.getSQLState());
        }
    }

    private Map<Integer, Stock> reconstruir(Connection conexion) throws SQLException {
        Map<Integer, Stock> stocks = new HashMap<>();
        try (Statement consulta = conexion.createStatement(); ResultSet filas = consulta.executeQuery(
                "SELECT p.*, s.tipo, s.stock_fisico, s.stock_exhibicion, s.stock_reservado FROM producto p JOIN stock s ON p.id = s.id")) {
            while (filas.next()) {
                Producto producto = new Producto(filas.getInt("id"), filas.getString("nombre"),
                        filas.getString("descripcion"), filas.getDouble("precio"));
                stocks.put(producto.getId(), "SIMPLE".equals(filas.getString("tipo"))
                        ? new StockSimple(producto, filas.getInt("stock_fisico"), filas.getInt("stock_exhibicion"), filas.getInt("stock_reservado"))
                        : new ComboStock(producto));
            }
        }
        try (Statement consulta = conexion.createStatement(); ResultSet filas = consulta.executeQuery("SELECT * FROM linea_combo")) {
            while (filas.next()) ((ComboStock) stocks.get(filas.getInt("combo_id"))).agregar(
                    stocks.get(filas.getInt("componente_id")), filas.getInt("cantidad_en_combo"));
        }
        return stocks;
    }
}
