package ar.com.coto.stock.persistence;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** El factor fijo de la política estacional pasa a ser una temporada de todo el año, sin cambiar pedidos. */
class UpgradeTemporadasTest {
    static final String[] CADENA = Stream.concat(Arrays.stream(UpgradeDestinatariosTest.CADENA),
            Stream.of("/db/upgrade-temporadas.sql")).toArray(String[]::new);

    private void ejecutar(Connection conexion, String archivo) throws Exception {
        try (var lector = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(archivo)), StandardCharsets.UTF_8)) {
            RunScript.execute(conexion, lector);
        }
    }
    private String valor(Connection conexion, String sql) throws SQLException {
        try (var sentencia = conexion.createStatement(); var resultado = sentencia.executeQuery(sql)) {
            assertTrue(resultado.next()); return resultado.getString(1);
        }
    }

    @Test void migraElFactorAnteriorYEsIdempotente() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")) {
            for (String archivo : UpgradeDestinatariosTest.CADENA) ejecutar(conexion, archivo);
            try (var sentencia = conexion.createStatement()) {
                // Un factor en una política no estacional nunca se usó: no genera temporada.
                sentencia.executeUpdate("UPDATE politica_reposicion SET factor_estacional = 2 WHERE stock_id = 1");
                // Un objetivo estacional que con ×10 no entra en un entero se limita al máximo admitido.
                sentencia.executeUpdate("UPDATE politica_reposicion SET stock_objetivo = 300000000 WHERE stock_id = 5");
            }
            ejecutar(conexion, "/db/upgrade-temporadas.sql");
            ejecutar(conexion, "/db/upgrade-temporadas.sql");

            assertEquals("1", valor(conexion, "SELECT COUNT(*) FROM temporada_reposicion"));
            assertEquals("5|1|1|31|12|1.5000", valor(conexion, "SELECT a.stock_id || '|' || t.desde_dia || '|' || t.desde_mes || '|' "
                    + "|| t.hasta_dia || '|' || t.hasta_mes || '|' || t.factor FROM temporada_reposicion t JOIN temporada_articulo a ON a.temporada_id = t.id"));
            assertEquals("1", valor(conexion, "SELECT COUNT(*) FROM temporada_articulo"));
            for (String[] columna : new String[][]{{"politica_reposicion", "factor_estacional"}, {"temporada_reposicion", "origen_migracion"}}) {
                assertEquals("0", valor(conexion, "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = '%s' AND column_name = '%s'"
                        .formatted(columna[0], columna[1])), columna[1]);
            }
            assertEquals("5", valor(conexion, "SELECT COUNT(*) FROM politica_reposicion"));
            assertEquals("214748364", valor(conexion, "SELECT stock_objetivo FROM politica_reposicion WHERE stock_id = 5"));
            assertTrue(Long.parseLong(valor(conexion, "SELECT MIN(id) FROM temporada_reposicion")) >= 1000);
            try (var sentencia = conexion.createStatement()) {
                sentencia.executeUpdate("INSERT INTO temporada_reposicion(id, nombre, desde_mes, desde_dia, hasta_mes, hasta_dia, factor) "
                        + "VALUES (50, 'Fiestas', 12, 1, 1, 6, 1.2), (51, 'Invierno', 6, 1, 8, 31, 1.2)");
                sentencia.executeUpdate("UPDATE temporada_reposicion SET categoria_id = 1 WHERE id = 51");
                sentencia.executeUpdate("INSERT INTO temporada_articulo(temporada_id, stock_id) VALUES (50, 1), (50, 2)");
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("INSERT INTO temporada_articulo(temporada_id, stock_id) VALUES (50, 1)"));
                // Los combos no tienen política ni temporada propia.
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("INSERT INTO temporada_articulo(temporada_id, stock_id) VALUES (50, 6)"));
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("INSERT INTO temporada_articulo(temporada_id, stock_id) VALUES (50, 999)"));
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("INSERT INTO temporada_reposicion(nombre, "
                        + "desde_mes, desde_dia, hasta_mes, hasta_dia, factor) VALUES ('Excesivo', 1, 1, 1, 2, 11)"));
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("INSERT INTO temporada_reposicion(nombre, "
                        + "desde_mes, desde_dia, hasta_mes, hasta_dia, factor) VALUES ('Mes 13', 13, 1, 1, 2, 1.2)"));
                // Borrar la temporada borra sus artículos.
                sentencia.executeUpdate("DELETE FROM temporada_reposicion WHERE id = 50");
                assertEquals("1", valor(conexion, "SELECT COUNT(*) FROM temporada_articulo"));
                // Una política nueva ya no lleva factor.
                sentencia.executeUpdate("DELETE FROM politica_reposicion WHERE stock_id = 2");
                sentencia.executeUpdate("INSERT INTO politica_reposicion(stock_id, tipo, punto_pedido, stock_objetivo, demanda_diaria, "
                        + "dias_cobertura, umbral_alerta, destino_alerta) VALUES (2, 'ESTACIONAL', 0, 60, 0, 1, 10, 'compras@coto.example')");
            }
        }
    }
}
