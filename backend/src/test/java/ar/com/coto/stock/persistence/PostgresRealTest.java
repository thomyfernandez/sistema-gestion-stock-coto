package ar.com.coto.stock.persistence;

import ar.com.coto.stock.adapter.ApiCotoDigitalExterna;
import ar.com.coto.stock.adapter.ApiProveedorExterno;
import ar.com.coto.stock.adapter.SistemaERPExterno;
import ar.com.coto.stock.domain.model.MovimientoStock;
import ar.com.coto.stock.domain.model.StockInsuficienteException;
import ar.com.coto.stock.domain.model.StockSimple;
import ar.com.coto.stock.domain.model.TipoMovimiento;
import ar.com.coto.stock.service.GestionInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Perfil postgres contra un servidor PostgreSQL real (Testcontainers). Inicializa con schema.sql y data.sql,
 * valida el mapeo JPA y recorre los flujos que dependen del motor: bloqueos, bytea, paginación y migraciones.
 * Sin Docker disponible, la clase se omite.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "coto.integraciones.automaticas=false",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "supervisor.demo", roles = {"SUPERVISOR", "COMPRAS", "RECEPCION", "REPOSITOR"})
@ActiveProfiles("postgres")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresRealTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("coto_stock").withUsername("coto").withPassword("coto");

    @DynamicPropertySource static void baseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", postgres::getJdbcUrl);
        registro.add("spring.datasource.username", postgres::getUsername);
        registro.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired GestionInventario inventario;
    @Autowired ApiProveedorExterno mensajes;
    @Autowired SistemaERPExterno erp;
    @Autowired ApiCotoDigitalExterna digital;

    @BeforeEach void reiniciar() {
        for (String tabla : List.of("usuario", "imagen_producto", "configuracion_avisos", "evento_integracion", "promocion", "politica_reposicion", "auditoria_stock", "pedido_reposicion",
                "movimiento_stock", "linea_combo", "stock", "producto", "destinatario_aviso", "categoria", "proveedor", "bloqueo_operacion")) jdbc.update("DELETE FROM " + tabla);
        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
        mensajes.setDisponible(true); erp.setDisponible(true); digital.setDisponible(true);
        mensajes.limpiar(); erp.limpiar(); digital.limpiar();
    }
    private ResultActions enviar(String ruta, String json) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json));
    }
    private static String operacion(int id, int cantidad) {
        return """
            {"stockId":%d,"cantidad":%d,"motivo":"Prueba PostgreSQL","usuario":"demo"}
            """.formatted(id, cantidad);
    }

    @Test void elMotorEsPostgresYHibernateValidaElEsquema() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT version()", String.class).startsWith("PostgreSQL 17"));
        mvc.perform(get("/api/productos")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].disponible").value(80));
        mvc.perform(get("/api/combos/6")).andExpect(jsonPath("$.disponible").value(35));
    }

    @Test void loginJwtUsaElUsuarioPersistidoConBcrypt() throws Exception {
        enviar("/api/auth/login", "{\"username\":\"supervisor.demo\",\"password\":\"coto123\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString()).andExpect(jsonPath("$.rol").value("SUPERVISOR"));
        enviar("/api/auth/login", "{\"username\":\"supervisor.demo\",\"password\":\"incorrecta\"}").andExpect(status().isUnauthorized());
    }

    @Test void reservaDeComboPersisteMovimientoAuditoriaYOutboxEnLaMismaTransaccion() throws Exception {
        enviar("/api/digital/reservas", operacion(6, 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.disponibilidades['6']").value(33)).andExpect(jsonPath("$.disponibilidades['1']").value(76));
        assertEquals(15, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_stock", Integer.class));
        assertEquals(14, jdbc.queryForObject("SELECT stock_reservado FROM stock WHERE id = 1", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM evento_integracion WHERE tipo = 'STOCK_DIGITAL'", Integer.class) >= 3);
        assertTrue(jdbc.queryForObject("SELECT MIN(id) FROM movimiento_stock WHERE motivo = 'Prueba PostgreSQL'", Long.class) >= 1000);
    }

    @Test void avisosPorListaGeneranUnEmailPorDireccion() throws Exception {
        mvc.perform(put("/api/configuracion/destinatarios/categorias/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"almacen@coto.example\",\"Jefe.Almacen@coto.example\"]}")).andExpect(status().isOk());
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        assertEquals(List.of("almacen@coto.example", "jefe.almacen@coto.example"), mensajes.getMensajes().stream().map(m -> m.to()).toList());
        mvc.perform(get("/api/configuracion/destinatarios")).andExpect(jsonPath("$[0].categoriaId").value(nullValue()))
                .andExpect(jsonPath("$[1].categoriaId").value(1)).andExpect(jsonPath("$[2].categoriaId").value(3));
    }

    @Test void politicasYPaginacionResuelvenEnElMotor() throws Exception {
        mvc.perform(get("/api/politicas")).andExpect(jsonPath("$", hasSize(5))).andExpect(jsonPath("$[*].stockId", contains(1, 2, 3, 4, 5)));
        mvc.perform(get("/api/movimientos?limite=5&pagina=2")).andExpect(header().string("X-Total-Count", "14")).andExpect(jsonPath("$", hasSize(4)));
        mvc.perform(get("/api/movimientos?limite=50&tipo=RECEPCION,RESERVA_DIGITAL&stockId=1")).andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[*].id", contains(3, 1)));
        mvc.perform(get("/api/auditoria?limite=10&stockId=3")).andExpect(header().string("X-Total-Count", "1"));
    }

    @Test void imagenSeGuardaComoByteaYSeRecupera() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        var creado = enviar("/api/productos", """
                {"nombre":"Artículo con imagen","descripcion":"Prueba","precio":1200,"imagen":{"tipo":"image/png","contenido":"%s"}}
                """.formatted(java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()))).andExpect(status().isCreated()).andReturn();
        int id = com.jayway.jsonpath.JsonPath.read(creado.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/productos/" + id + "/imagen")).andExpect(status().isOk()).andExpect(content().bytes(bytes.toByteArray()));
    }

    /** Doce reservas simultáneas sobre seis unidades: el bloqueo SELECT … FOR UPDATE impide sobrevender. */
    @Test void reservasConcurrentesNoSobrevendenGraciasAlBloqueoPesimista() throws Exception {
        List<Callable<Boolean>> tareas = new ArrayList<>();
        for (int i = 0; i < 12; i++) tareas.add(() -> {
            try {
                inventario.registrarMovimiento(new MovimientoStock(3, TipoMovimiento.RESERVA_DIGITAL, 1, Instant.now(), "Reserva concurrente", "digital.demo"));
                return true;
            } catch (StockInsuficienteException e) { return false; }
        });
        int confirmadas = 0;
        try (var executor = Executors.newFixedThreadPool(12)) {
            for (Future<Boolean> futuro : executor.invokeAll(tareas)) {
                try { if (futuro.get()) confirmadas++; } catch (ExecutionException e) { fail(e.getCause()); }
            }
        }
        assertEquals(6, confirmadas);
        var leche = (StockSimple) inventario.consultarStock(3);
        assertEquals(0, leche.getDisponible());
        assertEquals(24, leche.getStockReservado());
        assertEquals(14 + 6, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_stock", Integer.class));
    }

    /** La cadena de scripts de actualización desde v0.1 también corre en PostgreSQL. */
    @Test void migracionesDesdeV01CorrenEnPostgres() throws Exception {
        jdbc.execute("DROP DATABASE IF EXISTS coto_upgrade");
        jdbc.execute("CREATE DATABASE coto_upgrade");
        String url = postgres.getJdbcUrl().replace("/coto_stock", "/coto_upgrade");
        try (var conexion = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword())) {
            for (String archivo : UpgradeDestinatariosTest.CADENA) {
                ScriptUtils.executeSqlScript(conexion, new EncodedResource(new ClassPathResource(archivo.substring(1)), StandardCharsets.UTF_8));
            }
            ScriptUtils.executeSqlScript(conexion, new EncodedResource(new ClassPathResource("db/upgrade-destinatarios.sql"), StandardCharsets.UTF_8));
            try (var sentencia = conexion.createStatement()) {
                for (String[] caso : new String[][]{
                        {"SELECT COUNT(*) FROM producto", "6"},
                        {"SELECT stock_fisico - stock_exhibicion - stock_reservado FROM stock WHERE id = 1", "80"},
                        {"SELECT COUNT(*) FROM movimiento_stock", "14"},
                        {"SELECT COUNT(*) FROM categoria", "5"},
                        {"SELECT COUNT(*) FROM usuario", "4"},
                        {"SELECT COUNT(*) FROM destinatario_aviso", "0"}}) {
                    try (var resultado = sentencia.executeQuery(caso[0])) {
                        assertTrue(resultado.next()); assertEquals(caso[1], resultado.getString(1), caso[0]);
                    }
                }
                sentencia.executeUpdate("INSERT INTO movimiento_stock(stock_id, tipo, cantidad, motivo, usuario) VALUES (6, 'ARMADO_COMBO', 1, 'Prueba', 'demo')");
            }
        }
    }
}
