package ar.com.coto.stock.persistence;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Objects;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeEtapaDosTest {
    @Test void actualizacionPreservaElInventarioYRecuperaLosPedidosPendientes() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")) {
            for (String archivo : new String[]{"/etapa1/schema.sql", "/etapa1/data.sql", "/db/upgrade-etapa2.sql"}) {
                try (var lector = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(archivo)), StandardCharsets.UTF_8)) {
                    RunScript.execute(conexion, lector);
                }
            }
            try (var sentencia = conexion.createStatement()) {
                for (String[] caso : new String[][]{
                        {"SELECT COUNT(*) FROM producto", "6"},
                        {"SELECT stock_fisico-stock_exhibicion-stock_reservado FROM stock WHERE id=1", "80"},
                        {"SELECT COUNT(*) FROM movimiento_stock", "14"},
                        {"SELECT COUNT(*) FROM auditoria_stock", "6"},
                        {"SELECT COUNT(*) FROM politica_reposicion", "5"},
                        {"SELECT COUNT(*) FROM promocion", "3"},
                        {"SELECT clave FROM evento_integracion", "pedido:1"}}) {
                    try (var resultado = sentencia.executeQuery(caso[0])) {
                        assertTrue(resultado.next()); assertEquals(caso[1], resultado.getString(1));
                    }
                }
            }
        }
    }
}
