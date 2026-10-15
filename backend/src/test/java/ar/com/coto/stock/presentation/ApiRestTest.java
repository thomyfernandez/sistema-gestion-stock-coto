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
@AutoConfigureMockMvc
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

    @BeforeEach void reiniciar() {
        for (String tabla : List.of("evento_integracion", "promocion", "politica_reposicion", "auditoria_stock", "pedido_reposicion",
                "movimiento_stock", "linea_combo", "stock", "producto", "bloqueo_operacion")) jdbc.update("DELETE FROM " + tabla);
        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
        mensajes.setDisponible(true); erp.setDisponible(true); digital.setDisponible(true);
        mensajes.limpiar(); erp.limpiar(); digital.limpiar();
        notificaciones.setCanal(GestionNotificaciones.Canal.EMAIL);
    }
    private ResultActions enviar(String ruta, String json) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json));
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
        MvcResult reserva = enviar("/api/digital/reservas", operacion(6, 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.disponibilidades.6").value(33)).andReturn();
        mvc.perform(get(reserva.getResponse().getHeader("Location"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("RESERVA_DIGITAL")).andExpect(jsonPath("$.usuario").value("demo"));
        enviar("/api/digital/cancelaciones", operacion(6, 1)).andExpect(status().isCreated());
        enviar("/api/digital/despachos", operacion(6, 1)).andExpect(status().isCreated());
        mvc.perform(get("/api/combos/6")).andExpect(jsonPath("$.disponible").value(34));
        mvc.perform(get("/api/movimientos?stockId=6")).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/auditoria?stockId=6")).andExpect(jsonPath("$", hasSize(4)));
    }
    @Test void movimientoGenericoTambienPasaPorElGestor() throws Exception {
        enviar("/api/movimientos", """
                {"stockId":1,"tipo":"DEVOLUCION","cantidad":2,"motivo":"Devolución apta","usuario":"demo"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.disponibilidades.1").value(82));
    }
    @Test void stockInsuficienteDevuelveProblemDetail409SinCambios() throws Exception {
        enviar("/api/digital/reservas", operacion(6, 36)).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE")).andExpect(jsonPath("$.instance").value("/api/digital/reservas"))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(get("/api/combos/6")).andExpect(jsonPath("$.disponible").value(35));
        assertEquals(14, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_stock", Integer.class));
    }
    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"stockId\":1,\"motivo\":\"Prueba\",\"usuario\":\"demo\"}",
            "{\"stockId\":1,\"cantidad\":0,\"motivo\":\"Prueba\",\"usuario\":\"demo\"}",
            "{\"stockId\":1,\"cantidad\":-1,\"motivo\":\"Prueba\",\"usuario\":\"demo\"}",
            "{\"stockId\":1,\"cantidad\":1.5,\"motivo\":\"Prueba\",\"usuario\":\"demo\"}",
            "{\"stockId\":1,\"cantidad\":1,\"motivo\":\" \",\"usuario\":\"demo\"}",
            "{\"stockId\":1,\"cantidad\":1,\"motivo\":\"Prueba\",\"usuario\":\"demo\",\"fecha\":\"2026-01-01\"}",
            "{\"stockId\":1,\"cantidad\":2147483648,\"motivo\":\"Prueba\",\"usuario\":\"demo\"}",
            "null", "{"
    })
    void jsonYCantidadesInvalidasSeRechazanSinEscribir(String cuerpo) throws Exception {
        enviar("/api/digital/reservas", cuerpo).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertEquals(14, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_stock", Integer.class));
    }
    @Test void validacionDeProductosDevuelveErroresPorCampo() throws Exception {
        enviar("/api/productos", """
                {"nombre":" ","descripcion":"Prueba","precio":12.345}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores.nombre").isArray()).andExpect(jsonPath("$.errores.precio").isArray());
    }
    @Test void validacionAnidadaDetectaComponentesNulosYCantidadesInvalidas() throws Exception {
        enviar("/api/combos", """
                {"nombre":"Combo","descripcion":"Prueba","precio":100,"componentes":[{"stockId":1,"cantidad":0}]}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores['componentes[0].cantidad']").isArray());
        enviar("/api/combos", """
                {"nombre":"Combo","descripcion":"Prueba","precio":100,"componentes":[null]}
                """).andExpect(status().isBadRequest());
    }
    @ParameterizedTest
    @ValueSource(strings = {"/api/productos/0", "/api/productos/-1", "/api/productos/texto", "/api/productos/1/precio?cantidad=0",
            "/api/productos?tipo=DESCONOCIDO", "/api/movimientos?stockId=-1", "/api/pedidos?estado=INVENTADO"})
    void parametrosDeRutaYConsultaInvalidosDan400(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"/api/productos/999", "/api/movimientos/999", "/api/pedidos/999", "/api/productos/2/promociones/1"})
    void recursoInexistenteDa404(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
    @Test void erroresDeProtocoloTambienUsanRespuestasEstructuradas() throws Exception {
        mvc.perform(delete("/api/productos/1")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.codigo").value("HTTP_405"));
        mvc.perform(post("/api/productos").contentType(MediaType.TEXT_PLAIN).content("prueba"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.codigo").value("HTTP_415"));
        mvc.perform(get("/api/ruta-inexistente")).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("HTTP_404"));
    }
    @Test void preciosYCatalogoExponenPromocionesAcumuladas() throws Exception {
        mvc.perform(get("/api/productos/1/precio?cantidad=3&medioPago=DEBITO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.precioLista").value(5670)).andExpect(jsonPath("$.precioFinal").value(2891.70))
                .andExpect(jsonPath("$.promocionesAplicadas", hasSize(3)));
        mvc.perform(get("/api/digital/catalogo?cantidad=3&medioPago=DEBITO")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6))).andExpect(jsonPath("$[0].precio.precioFinal").value(2891.70))
                .andExpect(jsonPath("$[5].disponible").value(35));
    }
    @Test void promocionesSeCreanSeConsultanYSeDesactivan() throws Exception {
        MvcResult promo = enviar("/api/productos/2/promociones", """
                {"tipo":"DESCUENTO","porcentaje":12.5,"orden":1,"activa":true}
                """).andExpect(status().isCreated()).andReturn();
        String location = promo.getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value((int) id(promo)));
        mvc.perform(get("/api/productos/2/precio")).andExpect(jsonPath("$.precioFinal").value(2878.75));
        mvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content("""
                {"tipo":"DESCUENTO","porcentaje":12.5,"orden":1,"activa":false}
                """)).andExpect(status().isOk());
        mvc.perform(get("/api/productos/2/precio")).andExpect(jsonPath("$.precioFinal").value(3290));
    }
    @Test void conflictoSqlNoExponeDetallesDeInfraestructura() throws Exception {
        enviar("/api/productos/1/promociones", """
                {"tipo":"DESCUENTO","porcentaje":10,"orden":10,"activa":true}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONFLICTO_DATOS"))
                .andExpect(content().string(not(containsString("insert into")))).andExpect(jsonPath("$.trace").doesNotExist());
    }
    @Test void reglaDePromocionDesconocidaOIncompatibleSeRechaza() throws Exception {
        enviar("/api/productos/2/promociones", """
                {"tipo":"MEDIO_PAGO","porcentaje":10,"orden":1,"activa":true}
                """).andExpect(status().isBadRequest());
        enviar("/api/productos/2/promociones", """
                {"tipo":"DOS_POR_UNO","porcentaje":10,"orden":1,"activa":true}
                """).andExpect(status().isBadRequest());
    }
    @Test void politicaConfigurableCambiaLaReposicionAutomatica() throws Exception {
        mvc.perform(get("/api/productos/1/politica-reposicion")).andExpect(jsonPath("$.tipo").value("PUNTO_PEDIDO"));
        mvc.perform(put("/api/productos/1/politica-reposicion").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipo":"DEMANDA","puntoPedido":0,"stockObjetivo":200,"demandaDiaria":100,"diasCobertura":2,
                 "factorEstacional":1,"umbralAlerta":10,"destinoAlerta":"compras@coto.example"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("DEMANDA"));
        enviar("/api/reposicion/evaluaciones", "").andExpect(status().isOk());
        mvc.perform(get("/api/pedidos?stockId=1&estado=ENVIADO")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].cantidad").value(120));
        enviar("/api/reposicion/evaluaciones", "").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }
    @Test void observerStrategyYAdapterSePruebanCambiandoElCanal() throws Exception {
        mvc.perform(put("/api/notificaciones/canal").contentType(MediaType.APPLICATION_JSON).content("{\"canal\":\"SMS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canal").value("SMS"));
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        mvc.perform(get("/api/integraciones")).andExpect(status().isOk()).andExpect(jsonPath("$.mensajes", hasSize(1)))
                .andExpect(jsonPath("$.mensajes[0].canal").value("SMS")).andExpect(jsonPath("$.pedidosERP", hasSize(2)))
                .andExpect(jsonPath("$.catalogoDigital.1").value("19"));
        mvc.perform(get("/api/notificaciones/canal")).andExpect(jsonPath("$.canal").value("SMS"));
    }
    @Test void proveedorCaidoSeInspeccionaYSeRecuperaSinPerderStockConfirmado() throws Exception {
        mvc.perform(patch("/api/demo/proveedores/COTO_DIGITAL").contentType(MediaType.APPLICATION_JSON).content("{\"disponible\":false}"))
                .andExpect(status().isOk());
        enviar("/api/digital/reservas", operacion(1, 1)).andExpect(status().isCreated());
        mvc.perform(get("/api/integraciones")).andExpect(jsonPath("$.proveedoresDisponibles.COTO_DIGITAL").value(false))
                .andExpect(jsonPath("$.entregas[?(@.estado == 'PENDIENTE')]", hasSize(2)));
        mvc.perform(patch("/api/demo/proveedores/COTO_DIGITAL").contentType(MediaType.APPLICATION_JSON).content("{\"disponible\":true}"))
                .andExpect(status().isOk());
        enviar("/api/integraciones/reintentos", "").andExpect(jsonPath("$.procesados").value(2));
        mvc.perform(get("/api/integraciones")).andExpect(jsonPath("$.catalogoDigital.1").value("79"))
                .andExpect(jsonPath("$.entregas[?(@.estado == 'PENDIENTE')]", hasSize(0)));
    }
    @Test void pedidoSeRecibeUnaVezYNoAceptaSegundaRecepcion() throws Exception {
        enviar("/api/integraciones/reintentos", "").andExpect(status().isOk());
        enviar("/api/pedidos/1/recepcion", "{\"usuario\":\"recepcion.demo\"}").andExpect(status().isCreated());
        mvc.perform(get("/api/pedidos/1")).andExpect(jsonPath("$.estado").value("RECIBIDO"));
        mvc.perform(get("/api/productos/3")).andExpect(jsonPath("$.disponible").value(36));
        enviar("/api/pedidos/1/recepcion", "{\"usuario\":\"recepcion.demo\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INCOMPATIBLE"));
    }
    @Test void cancelarPedidoPendienteDevuelve204() throws Exception {
        enviar("/api/pedidos/1/cancelacion", "").andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/pedidos/1")).andExpect(jsonPath("$.estado").value("CANCELADO"));
    }
    @Test void corsPermiteElFrontendLocalYRechazaOrigenesAjeno() throws Exception {
        mvc.perform(options("/api/productos").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/productos").header("Origin", "https://otro.example").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
