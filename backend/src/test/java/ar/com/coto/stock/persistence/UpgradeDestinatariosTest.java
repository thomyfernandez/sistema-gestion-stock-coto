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

/** Cadena completa de migraciones desde v0.1 hasta las listas de destinatarios. */
class UpgradeDestinatariosTest {
    static final String[] CADENA = {"/etapa1/schema.sql", "/etapa1/data.sql", "/db/upgrade-etapa2.sql", "/db/upgrade-backoffice.sql",
            "/db/upgrade-proveedores.sql", "/db/upgrade-categorias.sql", "/db/upgrade-auth.sql", "/db/upgrade-combos-stock.sql", "/db/upgrade-destinatarios.sql"};

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

    @Test void migracionEsIdempotenteYConservaLasAsignacionesAnteriores() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")) {
            for (String archivo : CADENA) ejecutar(conexion, archivo);
            ejecutar(conexion, "/db/upgrade-destinatarios.sql");
            assertEquals("0", valor(conexion, "SELECT COUNT(*) FROM destinatario_aviso"));
            assertEquals("5", valor(conexion, "SELECT COUNT(*) FROM politica_reposicion"));
            assertEquals("compras@coto.example", valor(conexion, "SELECT email FROM configuracion_avisos WHERE id = 1"));
            try (var sentencia = conexion.createStatement()) {
                sentencia.executeUpdate("INSERT INTO destinatario_aviso(categoria_id, email) VALUES (3, 'verduleria@coto.example'), (NULL, 'sucursal@coto.example')");
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("INSERT INTO destinatario_aviso(categoria_id, email) VALUES (3, 'verduleria@coto.example')"));
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("INSERT INTO destinatario_aviso(categoria_id, email) VALUES (999, 'x@coto.example')"));
                assertThrows(SQLException.class, () -> sentencia.executeUpdate("INSERT INTO destinatario_aviso(categoria_id, email) VALUES (NULL, ' ')"));
            }
            assertTrue(Integer.parseInt(valor(conexion, "SELECT MIN(id) FROM destinatario_aviso")) >= 1000);
        }
    }
}
