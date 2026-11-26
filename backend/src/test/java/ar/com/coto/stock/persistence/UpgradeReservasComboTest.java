package ar.com.coto.stock.persistence;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** La columna de reservas virtuales de combos se agrega sobre la cadena completa y la migración es idempotente. */
class UpgradeReservasComboTest {
    static final String SCRIPT = "/db/upgrade-reservas-combo.sql";

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

    @Test void agregaLaColumnaConCeroYRechazaValoresInvalidos() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")) {
            for (String archivo : UpgradeDestinatariosTest.CADENA) ejecutar(conexion, archivo);
            ejecutar(conexion, SCRIPT);
            ejecutar(conexion, SCRIPT);
            assertEquals("0", valor(conexion, "SELECT COALESCE(SUM(stock_reservado_virtual), 0) FROM stock"));
            assertEquals("80", valor(conexion, "SELECT stock_fisico - stock_exhibicion - stock_reservado FROM stock WHERE id = 1"));
            try (var sentencia = conexion.createStatement()) {
                sentencia.executeUpdate("UPDATE stock SET stock_reservado_virtual = 2 WHERE tipo = 'COMBO'");
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("UPDATE stock SET stock_reservado_virtual = 1 WHERE id = 1"));
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("UPDATE stock SET stock_reservado_virtual = -1 WHERE tipo = 'COMBO'"));
            }
        }
    }
}
