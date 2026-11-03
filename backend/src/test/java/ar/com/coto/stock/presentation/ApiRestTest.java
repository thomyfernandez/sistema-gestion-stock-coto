package ar.com.coto.stock.presentation;

import ar.com.coto.stock.adapter.*;
import ar.com.coto.stock.service.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import javax.sql.DataSource;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Peticiones HTTP completas: controladores, validación, servicios, JPA y adaptadores reales del prototipo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "coto.integraciones.automaticas=false",
        "spring.datasource.url=jdbc:h2:mem:etapa3test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000"
})
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.security.test.context.support.WithMockUser(username = "supervisor.demo", roles = {"SUPERVISOR", "COMPRAS", "RECEPCION", "REPOSITOR"})
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiRestTest {
    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApiProveedorExterno mensajes;
    @Autowired SistemaERPExterno erp;
    @Autowired ApiCotoDigitalExterna digital;
    @Autowired GestionNotificaciones notificaciones;
    @Autowired ar.com.coto.stock.config.JwtTokenService jwtTokenService;
    private String tokenPruebas;

    @BeforeEach void reiniciar() {
        for (String tabla : List.of("usuario", "imagen_producto", "configuracion_avisos", "destinatario_aviso", "evento_integracion", "promocion", "politica_reposicion", "auditoria_stock", "pedido_reposicion",
                "movimiento_stock", "linea_combo", "stock", "producto", "categoria", "proveedor", "bloqueo_operacion")) jdbc.update("DELETE FROM " + tabla);
        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
        mensajes.setDisponible(true); erp.setDisponible(true); digital.setDisponible(true);
        mensajes.limpiar(); erp.limpiar(); digital.limpiar();
        notificaciones.setCanal(GestionNotificaciones.Canal.EMAIL);
        tokenPruebas = jwtTokenService.generarToken("supervisor.demo", "SUPERVISOR", "Supervisor de Sucursal");
    }
    private ResultActions enviar(String ruta, String json) throws Exception {
        return mvc.perform(post(ruta).header("Authorization", "Bearer " + tokenPruebas).contentType(MediaType.APPLICATION_JSON).content(json));
    }
    private String operacion(int id, int cantidad) {
        return """
            {"stockId":%d,"cantidad":%d,"motivo":"Prueba HTTP","usuario":"demo"}
            """.formatted(id, cantidad);
    }
    private long id(MvcResult r) throws java.io.UnsupportedEncodingException {
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test void estadoYRaizPermitenDescubrirLaApi() throws Exception {
        mvc.perform(get("/api/estado")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.version").value("0.3.0"));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(jsonPath("$.productos").value("/api/productos"));
    }
    @Test void inventarioEsPlanoYCombosNoExponenContadoresNiObservadores() throws Exception {
        mvc.perform(get("/api/productos")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].disponible").value(80)).andExpect(jsonPath("$[0].precio").value(1890));
        mvc.perform(get("/api/combos/6")).andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("COMBO"))
                .andExpect(jsonPath("$.disponible").value(35)).andExpect(jsonPath("$.stockFisico").value(nullValue()))
                .andExpect(jsonPath("$.componentes", hasSize(2))).andExpect(jsonPath("$.componentes[0].cantidadEnCombo").value(2))
                .andExpect(jsonPath("$.observadores").doesNotExist());
        mvc.perform(get("/api/productos?tipo=SIMPLE")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(5)));
        mvc.perform(get("/api/combos")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/combos/1")).andExpect(status().isNotFound());
    }
    @Test void altaDeProductoDevuelveLocationConsultable() throws Exception {
        MvcResult creado = enviar("/api/productos", """
                {"nombre":"Yerba COTO","descripcion":"Paquete 1 kg","precio":3500.50}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.disponible").value(0)).andReturn();
        String location = creado.getResponse().getHeader("Location");
        assertNotNull(location);
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value((int) id(creado)))
                .andExpect(jsonPath("$.precio").value(3500.50));
    }
    @Test void comboAnidadoConHojaCompartidaSeCreaYSeReservaPorHttp() throws Exception {
        MvcResult creado = enviar("/api/combos", """
                {"nombre":"Canasta","descripcion":"Combo con arroz adicional","precio":8000,
                 "componentes":[{"stockId":6,"cantidad":1},{"stockId":1,"cantidad":1}]}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.disponible").value(26)).andReturn();
        enviar("/api/digital/reservas", operacion((int) id(creado), 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.disponibilidades.1").value(74));
        mvc.perform(get(creado.getResponse().getHeader("Location"))).andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(24));
    }
    @Test void componenteInexistenteDa404YRevierteElAlta() throws Exception {
        enviar("/api/combos", """
                {"nombre":"Combo inválido","descripcion":"Prueba","precio":100,"componentes":[{"stockId":999,"cantidad":1}]}
                """).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class));
    }
    @Test void recepcionYSucursalActualizanLasZonasSinDuplicarElFisico() throws Exception {
        enviar("/api/recepcion/ingresos", operacion(1, 5)).andExpect(status().isCreated());
        enviar("/api/sucursal/movimientos", """
                {"stockId":1,"tipo":"TRASLADO_A_GONDOLA","cantidad":10,"motivo":"Carga","usuario":"pda"}
                """).andExpect(status().isCreated());
        mvc.perform(get("/api/productos/1")).andExpect(jsonPath("$.stockFisico").value(125))
                .andExpect(jsonPath("$.stockExhibicion").value(40)).andExpect(jsonPath("$.disponible").value(75));
        enviar("/api/sucursal/movimientos", """
                {"stockId":1,"tipo":"MERMA_GONDOLA","cantidad":2,"motivo":"Rotura","usuario":"pda"}
                """).andExpect(status().isCreated());
        mvc.perform(get("/api/productos/1")).andExpect(jsonPath("$.stockFisico").value(123))
                .andExpect(jsonPath("$.stockExhibicion").value(38)).andExpect(jsonPath("$.disponible").value(75));
    }
    @Test void reservasCancelacionesYDespachosDeCombosTienenMovimientosConsultables() throws Exception {
        enviar("/api/digital/reservas", operacion(6, 1)).andExpect(status().isCreated());
        mvc.perform(get("/api/productos/6")).andExpect(jsonPath("$.disponible").value(34));
        enviar("/api/digital/cancelaciones", operacion(6, 1)).andExpect(status().isCreated());
        mvc.perform(get("/api/productos/6")).andExpect(jsonPath("$.disponible").value(35));
        enviar("/api/digital/reservas", operacion(6, 1)).andExpect(status().isCreated());
        enviar("/api/digital/despachos", operacion(6, 1)).andExpect(status().isCreated());
        mvc.perform(get("/api/productos/1")).andExpect(jsonPath("$.stockFisico").value(118));
        mvc.perform(get("/api/productos/2")).andExpect(jsonPath("$.stockFisico").value(54));
        mvc.perform(get("/api/movimientos?stockId=1")).andExpect(jsonPath("$", hasSize(6)));
    }
    @Test void movimientosFiltranPorStockYTipoYAuditoriaAsociaMovimiento() throws Exception {
        enviar("/api/recepcion/ingresos", operacion(1, 5)).andExpect(status().isCreated());
        mvc.perform(get("/api/movimientos?stockId=1&tipo=RECEPCION")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/movimientos/1")).andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(120));
        mvc.perform(get("/api/movimientos/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/auditoria?stockId=1")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].disponibleAnterior").value(90)).andExpect(jsonPath("$[0].disponibleActual").value(80));
    }
    @Test void pedidoSeEnvioAlErpYSeRecibePorHttp() throws Exception {
        mvc.perform(get("/api/pedidos/1")).andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(30))
                .andExpect(jsonPath("$.estado").value("ENVIADO"));
        mvc.perform(get("/api/pedidos?stockId=3&estado=ENVIADO")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(put("/api/pedidos/1/recepcion")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECIBIDO"));
        mvc.perform(get("/api/pedidos/1")).andExpect(jsonPath("$.estado").value("RECIBIDO"));
        mvc.perform(get("/api/productos/3")).andExpect(jsonPath("$.disponible").value(36));
        mvc.perform(put("/api/pedidos/1/recepcion")).andExpect(status().isConflict());
        mvc.perform(put("/api/pedidos/999/recepcion")).andExpect(status().isNotFound());
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content("""
                {"stockId":6,"cantidad":10}
                """)).andExpect(status().isBadRequest());
    }
    @Test void politicasExigenParametrosCoherentesConElTipo() throws Exception {
        for (String body : List.of(
                """
                {"tipo":"PUNTO_PEDIDO","puntoPedido":20,"stockObjetivo":20,"demandaDiaria":0,"diasCobertura":1,
                 "factorEstacional":1,"umbralAlerta":10}
                """,
                """
                {"tipo":"PUNTO_PEDIDO","puntoPedido":20,"stockObjetivo":15,"demandaDiaria":0,"diasCobertura":1,
                 "factorEstacional":1,"umbralAlerta":10}
                """,
                """
                {"tipo":"DEMANDA","puntoPedido":0,"stockObjetivo":0,"demandaDiaria":10,"diasCobertura":1,
                 "factorEstacional":1,"umbralAlerta":10}
                """,
                """
                {"tipo":"DEMANDA","puntoPedido":0,"stockObjetivo":100,"demandaDiaria":0,"diasCobertura":1,
                 "factorEstacional":1,"umbralAlerta":10}
                """,
                """
                {"tipo":"ESTACIONAL","puntoPedido":0,"stockObjetivo":100,"demandaDiaria":0,"diasCobertura":1,
                 "factorEstacional":0,"umbralAlerta":10}
                """)) {
            mvc.perform(put("/api/productos/1/politica-reposicion").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        }
    }
    @Test void politicaConfigurableCambiaLaReposicionAutomatica() throws Exception {
        mvc.perform(get("/api/productos/1/politica-reposicion")).andExpect(jsonPath("$.tipo").value("PUNTO_PEDIDO"));
        mvc.perform(put("/api/productos/1/politica-reposicion").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipo":"DEMANDA","puntoPedido":0,"stockObjetivo":200,"demandaDiaria":100,"diasCobertura":2,
                 "factorEstacional":1,"umbralAlerta":10}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("DEMANDA"));
        enviar("/api/reposicion/evaluaciones", "").andExpect(status().isOk());
        mvc.perform(get("/api/pedidos?stockId=1&estado=ENVIADO")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].cantidad").value(120));
    }
    @Test void politicaDeComboSeRechazaConErrorEspecifico() throws Exception {
        mvc.perform(put("/api/productos/6/politica-reposicion").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipo":"PUNTO_PEDIDO","puntoPedido":20,"stockObjetivo":100,"demandaDiaria":0,"diasCobertura":1,
                 "factorEstacional":1,"umbralAlerta":10}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("POLITICA_REPOSICION_INVALIDA"));
    }
    @Test void operacionSinStockSuficienteDevuelve409YNoPersiste() throws Exception {
        enviar("/api/digital/reservas", operacion(1, 81)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
        mvc.perform(get("/api/productos/1")).andExpect(jsonPath("$.disponible").value(80));
    }
    @ParameterizedTest
    @ValueSource(strings = {"/api/productos", "/api/combos", "/api/recepcion/ingresos", "/api/digital/reservas",
            "/api/pedidos", "/api/sucursal/ajustes", "/api/configuracion/avisos", "/api/configuracion/proveedores", "/api/categorias"})
    void cuerposInvalidosDevuelven400ConDetalleRfc9457(String ruta) throws Exception {
        mvc.perform(post(ruta).header("Authorization", "Bearer " + tokenPruebas).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Solicitud inválida"))
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalidParams", not(empty())));
    }
    @Test void rutasInexistentesDevuelven404ConProblemJson() throws Exception {
        mvc.perform(get("/api/productos/999")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test void configuracionGlobalSePersisteYSeAplicaATodosLosProductos() throws Exception {
        mvc.perform(put("/api/configuracion/avisos").contentType(MediaType.APPLICATION_JSON).content("""
                {"canal":"EMAIL","email":"deposito@coto.example","telefono":"+541112345678"}
                """)).andExpect(status().isOk());
        assertEquals("deposito@coto.example", jdbc.queryForObject("SELECT email FROM configuracion_avisos WHERE id=1", String.class));
    }

    @Test void agendaDeContactosSeCreaEditaYConsultaDesdeLaBase() throws Exception {
        long contacto = crearContacto("Compras de Almacén", "arroz@contacto.example", "");
        mvc.perform(get("/api/configuracion/contactos/" + contacto)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Compras de Almacén"));
        mvc.perform(put("/api/configuracion/contactos/" + contacto).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Compras de Almacén - Central","email":"compras@molinos.example","telefono":"+541155551111"}
                """)).andExpect(status().isOk());
        mvc.perform(get("/api/configuracion/contactos")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].telefono").value("+541155551111"));
        assertEquals("Compras de Almacén - Central", jdbc.queryForObject("SELECT nombre FROM proveedor WHERE id=?", String.class, contacto));
        mvc.perform(get("/api/configuracion/contactos/999")).andExpect(status().isNotFound());
        mvc.perform(put("/api/configuracion/contactos/999").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Inexistente","email":"hola@contacto.example","telefono":""}
                """)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CA1 & CA2 — Endpoints REST para consultar y actualizar listas de correos general y por categoría")
    void destinatariosGeneralesYPorCategoriaSeListanYActualizanPorApi() throws Exception {
        // GET /api/configuracion/destinatarios
        mvc.perform(get("/api/configuracion/destinatarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.general", hasSize(2)))
                .andExpect(jsonPath("$.general[0]").value("compras@coto.example"))
                .andExpect(jsonPath("$.porCategoria.2", hasSize(2)))
                .andExpect(jsonPath("$.porCategoria.2[0]").value("frescos@coto.example"));

        // PUT /api/configuracion/destinatarios/general
        mvc.perform(put("/api/configuracion/destinatarios/general")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"emails":["nueva.sucursal@coto.com.ar", "almacen.central@coto.com.ar"]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value("nueva.sucursal@coto.com.ar"));

        // PUT /api/configuracion/destinatarios/categorias/2
        mvc.perform(put("/api/configuracion/destinatarios/categorias/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"emails":["lacteos.exclusivo@coto.com.ar"]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value("lacteos.exclusivo@coto.com.ar"));

        // Verificar persistencia reflejada en GET
        mvc.perform(get("/api/configuracion/destinatarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.general", hasSize(2)))
                .andExpect(jsonPath("$.porCategoria.2[0]").value("lacteos.exclusivo@coto.com.ar"));
    }

    @Test
    @DisplayName("CA1 — PUT /api/configuracion/destinatarios/general valida formato RFC de email")
    void destinatariosGeneralesValidaFormatoEmail() throws Exception {
        mvc.perform(put("/api/configuracion/destinatarios/general")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"emails":["email-invalido-sin-arroba"]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    @DisplayName("CA2 — PUT /api/configuracion/destinatarios/categorias/{id} rechaza categorías inexistentes o inválidas")
    void destinatariosPorCategoriaFallaSiCategoriaInexistente() throws Exception {
        mvc.perform(put("/api/configuracion/destinatarios/categorias/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"emails":["test@coto.com.ar"]}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        mvc.perform(put("/api/configuracion/destinatarios/categorias/0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"emails":["test@coto.com.ar"]}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CA4 — PUT /api/productos/{id}/politica-reposicion guarda sin requerir contactoId ni canalAlerta")
    void politicaReposicionSeActualizaSinCamposDeAvisoParticular() throws Exception {
        mvc.perform(put("/api/productos/1/politica-reposicion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"tipo":"DEMANDA","puntoPedido":0,"stockObjetivo":180,"demandaDiaria":12,"diasCobertura":3,
                         "factorEstacional":1.0,"umbralAlerta":15}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("DEMANDA"))
                .andExpect(jsonPath("$.stockObjetivo").value(180))
                .andExpect(jsonPath("$.umbralAlerta").value(15))
                .andExpect(jsonPath("$.contactoId").value(nullValue()))
                .andExpect(jsonPath("$.canalAlerta").value(nullValue()));

        // Verificar persistencia en base
        assertEquals(180, jdbc.queryForObject("SELECT stock_objetivo FROM politica_reposicion WHERE stock_id=1", Integer.class));
        assertEquals(15, jdbc.queryForObject("SELECT umbral_alerta FROM politica_reposicion WHERE stock_id=1", Integer.class));
    }

    @Test
    @DisplayName("CA4 — PUT /api/productos/{id}/politica-reposicion tolera atributos legados de contacto ignorándolos")
    void politicaReposicionIgnoraCamposDeAvisoHeredadosSinFallar() throws Exception {
        mvc.perform(put("/api/productos/1/politica-reposicion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"tipo":"PUNTO_PEDIDO","puntoPedido":25,"stockObjetivo":120,"demandaDiaria":0,"diasCobertura":1,
                         "factorEstacional":1,"umbralAlerta":20,"contactoId":123,"canalAlerta":"EMAIL","destinoAlerta":"test@coto.example"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntoPedido").value(25))
                .andExpect(jsonPath("$.stockObjetivo").value(120))
                .andExpect(jsonPath("$.contactoId").value(nullValue()))
                .andExpect(jsonPath("$.canalAlerta").value(nullValue()));
    }

    @Test
    @DisplayName("CA5 & CA6 — Avisos automáticos priorizan destinatarios de categoría y en su defecto lista general")
    void avisosPriorizanCategoriaYLuegoListaGeneral() throws Exception {
        mensajes.limpiar();
        // Leche (id 3) está en categoría Frescos (cat 2), con 2 emails configurados en data.sql.
        // Aceite (id 2) está en categoría Almacén (cat 1), sin emails configurados (recurre a la lista general).
        // Disponible Leche: 6, umbral 10. Reserva 1 -> cae a 5.
        enviar("/api/digital/reservas", operacion(3, 1)).andExpect(status().isCreated());
        // Disponible Aceite: 35, umbral 10. Reserva 26 -> cae a 9.
        enviar("/api/digital/reservas", operacion(2, 26)).andExpect(status().isCreated());

        // Se deben emitir 2 emails a Frescos + 2 emails a la Lista General = 4 mensajes
        assertEquals(4, mensajes.getMensajes().size());
        assertTrue(mensajes.getMensajes().stream().allMatch(m -> "EMAIL".equals(m.canal())));

        List<String> destinatariosEmitidos = mensajes.getMensajes().stream().map(ApiProveedorExterno.Mensaje::to).toList();
        // Frescos
        assertTrue(destinatariosEmitidos.contains("frescos@coto.example"));
        assertTrue(destinatariosEmitidos.contains("lacteos.sucursal@coto.com.ar"));
        // Lista general
        assertTrue(destinatariosEmitidos.contains("compras@coto.example"));
        assertTrue(destinatariosEmitidos.contains("sucursal42.stock@coto.com.ar"));
    }

    private long crearContacto(String nombre, String email, String telefono) throws Exception {
        return id(enviar("/api/configuracion/contactos", """
                {"nombre":"%s","email":"%s","telefono":"%s"}
                """.formatted(nombre, email, telefono)).andExpect(status().isCreated()).andReturn());
    }

    private ResultActions configurarCategoria(int categoriaId, String nombre, Long contactoId, String canal) throws Exception {
        return mvc.perform(put("/api/categorias/" + categoriaId).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"%s","contactoId":%s,"canalAlerta":%s}
                """.formatted(nombre, contactoId == null ? "null" : contactoId.toString(), canal == null ? "null" : "\"" + canal + "\"")));
    }
    @Test void categoriasInicialesClasificanSimplesYCombosYPermitenFiltrarLaApi() throws Exception {
        mvc.perform(get("/api/categorias")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(5)));
        mvc.perform(get("/api/productos?categoriaId=1")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
        mvc.perform(get("/api/productos?categoriaId=1&tipo=COMBO")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(6));
        mvc.perform(get("/api/productos?categoriaId=3")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].nombre").value("Bananas por kg"));
        mvc.perform(get("/api/productos?categoriaId=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/categorias/999")).andExpect(status().isNotFound());
    }
    @Test void categoriasSeCreanEditanYPersistenSinDuplicarNombres() throws Exception {
        long electro = id(enviar("/api/configuracion/contactos", "{\"nombre\":\"Sector Electro\",\"email\":\"electro@coto.example\"}")
                .andExpect(status().isCreated()).andReturn());
        configurarCategoria(4, "Electrodomésticos", electro, "EMAIL").andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Electrodomésticos"));
        mvc.perform(get("/api/categorias/4")).andExpect(jsonPath("$.contactoId").value(electro))
                .andExpect(jsonPath("$.canalAlerta").value("EMAIL"));
        mvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Electrodomésticos\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/productos/1/categoria").contentType(MediaType.APPLICATION_JSON).content("{\"categoriaId\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoriaId").value(1)).andExpect(jsonPath("$.stockFisico").value(120));
        mvc.perform(put("/api/productos/1/categoria").contentType(MediaType.APPLICATION_JSON).content("{\"categoriaId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoriaId").value(nullValue())).andExpect(jsonPath("$.disponible").value(80));
        mvc.perform(put("/api/productos/1/categoria").contentType(MediaType.APPLICATION_JSON).content("{\"categoriaId\":999}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/productos/999/categoria").contentType(MediaType.APPLICATION_JSON).content("{\"categoriaId\":1}"))
                .andExpect(status().isNotFound());
        assertEquals(14, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_stock", Integer.class));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_stock", Integer.class));
        mvc.perform(get("/api/productos/1")).andExpect(jsonPath("$.categoriaId").value(nullValue()));
    }

    @Test void datosDeContactoUsadosPorUnaCategoriaNoSePuedenEliminar() throws Exception {
        long contacto = crearContacto("Verdulería", "verduleria@coto.example", "+541155551111");
        configurarCategoria(3, "Verdulería", contacto, "EMAIL").andExpect(status().isOk());
        mvc.perform(put("/api/configuracion/contactos/" + contacto).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Verdulería","email":"","telefono":"+541155551111"}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("categoría Verdulería")));
        configurarCategoria(3, "Verdulería", contacto, "SMS").andExpect(status().isOk());
        mvc.perform(put("/api/configuracion/contactos/" + contacto).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Verdulería","email":"","telefono":"+541155551111"}
                """)).andExpect(status().isOk());
    }
    @Test void configuracionInvalidaDeCategoriaSeRechazaSinModificarla() throws Exception {
        long contacto = crearContacto("Sólo email", "sector@coto.example", "");
        configurarCategoria(3, "Verdulería", contacto, "SMS").andExpect(status().isBadRequest());
        configurarCategoria(3, "Verdulería", 999L, "EMAIL").andExpect(status().isNotFound());
        configurarCategoria(3, "Verdulería", null, "EMAIL").andExpect(status().isBadRequest());
        configurarCategoria(3, "Verdulería", contacto, null).andExpect(status().isBadRequest());
        enviar("/api/categorias", "{\"nombre\":\" \"}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/categorias/3")).andExpect(jsonPath("$.contactoId").value(nullValue()));
    }
}
