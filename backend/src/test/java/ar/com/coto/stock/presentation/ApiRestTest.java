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
        for (String tabla : List.of("imagen_producto", "configuracion_avisos", "evento_integracion", "promocion", "politica_reposicion", "auditoria_stock", "pedido_reposicion",
                "movimiento_stock", "linea_combo", "stock", "producto", "categoria", "proveedor", "bloqueo_operacion")) jdbc.update("DELETE FROM " + tabla);
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
    @Test void configuracionGlobalSePersisteYSeAplicaATodosLosProductos() throws Exception {
        mvc.perform(put("/api/configuracion/avisos").contentType(MediaType.APPLICATION_JSON).content("""
                {"canal":"EMAIL","email":"deposito@coto.example","telefono":"+541112345678"}
                """)).andExpect(status().isOk());
        assertEquals("deposito@coto.example", jdbc.queryForObject("SELECT email FROM configuracion_avisos WHERE id=1", String.class));
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        enviar("/api/digital/reservas", operacion(2, 26)).andExpect(status().isCreated());
        mvc.perform(get("/api/integraciones")).andExpect(jsonPath("$.mensajes", hasSize(2)))
                .andExpect(jsonPath("$.mensajes[0].destino").value("deposito@coto.example"))
                .andExpect(jsonPath("$.mensajes[1].destino").value("deposito@coto.example"));
    }
    @Test void cambiarDestinatarioNoReescribeLosAvisosPendientes() throws Exception {
        mvc.perform(patch("/api/demo/proveedores/MENSAJERIA").contentType(MediaType.APPLICATION_JSON).content("{\"disponible\":false}"));
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        mvc.perform(put("/api/configuracion/avisos").contentType(MediaType.APPLICATION_JSON).content("""
                {"canal":"WHATSAPP","email":"nuevo@coto.example","telefono":"+541112345678"}
                """)).andExpect(status().isOk());
        mvc.perform(patch("/api/demo/proveedores/MENSAJERIA").contentType(MediaType.APPLICATION_JSON).content("{\"disponible\":true}"));
        enviar("/api/integraciones/reintentos", "").andExpect(status().isOk());
        enviar("/api/digital/reservas", operacion(2, 26)).andExpect(status().isCreated());
        mvc.perform(get("/api/integraciones")).andExpect(jsonPath("$.mensajes[0].canal").value("EMAIL"))
                .andExpect(jsonPath("$.mensajes[0].destino").value("compras@coto.example"))
                .andExpect(jsonPath("$.mensajes[1].canal").value("WHATSAPP"))
                .andExpect(jsonPath("$.mensajes[1].destino").value("+541112345678"));
    }
    @Test void configuracionInvalidaSeRechazaSinCambiarLoGuardado() throws Exception {
        for (String body : List.of(
                "{\"canal\":\"SMS\",\"email\":\"compras@coto.example\",\"telefono\":\"\"}",
                "{\"canal\":\"EMAIL\",\"email\":\"invalido\",\"telefono\":\"\"}")) {
            mvc.perform(put("/api/configuracion/avisos").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/configuracion/avisos")).andExpect(jsonPath("$.canal").value("EMAIL"))
                .andExpect(jsonPath("$.email").value("compras@coto.example"));
    }
    private byte[] imagenPng() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
    @Test void imagenSeGuardaConElAltaYSeRecuperaPorHttp() throws Exception {
        byte[] imagen = imagenPng();
        String base64 = java.util.Base64.getEncoder().encodeToString(imagen);
        var creado = enviar("/api/productos", """
                {"nombre":"Artículo con imagen","descripcion":"Prueba","precio":1200,
                 "imagen":{"tipo":"image/png","contenido":"%s"}}
                """.formatted(base64)).andExpect(status().isCreated()).andExpect(jsonPath("$.imagenUrl").isString()).andReturn();
        int productoId = (int) id(creado);
        mvc.perform(get("/api/productos/" + productoId + "/imagen")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(content().bytes(imagen));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM imagen_producto WHERE producto_id=?", Integer.class, productoId));
        mvc.perform(get("/api/productos/" + productoId)).andExpect(jsonPath("$.imagenUrl").isString())
                .andExpect(jsonPath("$.contenido").doesNotExist());
    }
    @Test void imagenInvalidaNoCreaProductos() throws Exception {
        String imagen = java.util.Base64.getEncoder().encodeToString("archivo de texto".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        enviar("/api/productos", """
                {"nombre":"Inválido","descripcion":"Prueba","precio":1,"imagen":{"tipo":"image/png","contenido":"%s"}}
                """.formatted(imagen)).andExpect(status().isBadRequest());
        enviar("/api/productos", """
                {"nombre":"SVG","descripcion":"Prueba","precio":1,"imagen":{"tipo":"image/svg+xml","contenido":"PHN2Zy8+"}}
                """).andExpect(status().isBadRequest());
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM imagen_producto", Integer.class));
        mvc.perform(get("/api/productos/1/imagen")).andExpect(status().isNotFound());
    }
    @Test void comboInvalidoRevierteTambienSuImagen() throws Exception {
        enviar("/api/combos", """
                {"nombre":"Combo inválido","descripcion":"Prueba","precio":100,
                 "componentes":[{"stockId":999,"cantidad":1}],"imagen":{"tipo":"image/png","contenido":"%s"}}
                """.formatted(java.util.Base64.getEncoder().encodeToString(imagenPng()))).andExpect(status().isNotFound());
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM imagen_producto", Integer.class));
    }
    @Test void politicaYaNoRequiereUnDestinatarioPorProducto() throws Exception {
        mvc.perform(put("/api/productos/1/politica-reposicion").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipo":"PUNTO_PEDIDO","puntoPedido":20,"stockObjetivo":100,"demandaDiaria":0,"diasCobertura":1,
                 "factorEstacional":1,"umbralAlerta":10}
                """)).andExpect(status().isOk());
    }

    private long crearContacto(String nombre, String email, String telefono) throws Exception {
        return id(enviar("/api/configuracion/contactos", """
                {"nombre":"%s","email":"%s","telefono":"%s"}
                """.formatted(nombre, email, telefono)).andExpect(status().isCreated()).andReturn());
    }
    private ResultActions asignarAvisos(int stockId, Long contactoId, String canal) throws Exception {
        return mvc.perform(put("/api/productos/" + stockId + "/politica-reposicion").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipo":"PUNTO_PEDIDO","puntoPedido":20,"stockObjetivo":100,"demandaDiaria":0,"diasCobertura":1,
                 "factorEstacional":1,"umbralAlerta":20,"contactoId":%s,"canalAlerta":%s}
                """.formatted(contactoId == null ? "null" : contactoId.toString(), canal == null ? "null" : "\"" + canal + "\"")));
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
    @Test void cadaProductoAvisaASuContactoYLosDemasUsanLaSucursal() throws Exception {
        long arroz = crearContacto("Compras de Almacén", "arroz@contacto.example", "");
        long aceite = crearContacto("Supervisión de stock", "", "+541155552222");
        asignarAvisos(1, arroz, "EMAIL").andExpect(status().isOk()).andExpect(jsonPath("$.contactoId").value(arroz));
        asignarAvisos(2, aceite, "WHATSAPP").andExpect(status().isOk());
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        enviar("/api/digital/reservas", operacion(2, 26)).andExpect(status().isCreated());
        enviar("/api/digital/reservas", operacion(3, 1)).andExpect(status().isCreated());
        mvc.perform(get("/api/integraciones")).andExpect(jsonPath("$.mensajes", hasSize(3)))
                .andExpect(jsonPath("$.mensajes[0].destino").value("arroz@contacto.example"))
                .andExpect(jsonPath("$.mensajes[0].canal").value("EMAIL"))
                .andExpect(jsonPath("$.mensajes[1].destino").value("+541155552222"))
                .andExpect(jsonPath("$.mensajes[1].canal").value("WHATSAPP"))
                .andExpect(jsonPath("$.mensajes[2].destino").value("compras@coto.example"));
        mvc.perform(get("/api/productos/2/politica-reposicion")).andExpect(jsonPath("$.contactoId").value(aceite))
                .andExpect(jsonPath("$.canalAlerta").value("WHATSAPP"));
    }
    @Test void contactosInvalidosYCanalesSinDestinoSeRechazanSinCambiarLaPolitica() throws Exception {
        for (String body : List.of("{\"nombre\":\"Vacío\"}",
                "{\"nombre\":\"Inválido\",\"email\":\"sin-arroba\"}",
                "{\"nombre\":\"Inválido\",\"telefono\":\"abc\"}")) {
            enviar("/api/configuracion/contactos", body).andExpect(status().isBadRequest());
        }
        long email = crearContacto("Solo email", "aviso@contacto.example", "");
        long telefono = crearContacto("Solo teléfono", "", "+541155551111");
        asignarAvisos(1, email, "SMS").andExpect(status().isBadRequest());
        asignarAvisos(1, telefono, "EMAIL").andExpect(status().isBadRequest());
        asignarAvisos(1, null, "EMAIL").andExpect(status().isBadRequest());
        asignarAvisos(1, email, null).andExpect(status().isBadRequest());
        asignarAvisos(1, 999L, "EMAIL").andExpect(status().isNotFound());
        mvc.perform(get("/api/productos/1/politica-reposicion")).andExpect(jsonPath("$.contactoId").value(nullValue()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM proveedor", Integer.class));
        asignarAvisos(1, telefono, "SMS").andExpect(status().isOk());
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        assertEquals("SMS", mensajes.getMensajes().getFirst().canal());
        assertEquals("+541155551111", mensajes.getMensajes().getFirst().to());
    }
    @Test void editarContactoNoPuedeDejarSinDestinoUnArticuloAsignado() throws Exception {
        long contacto = crearContacto("Contacto", "original@contacto.example", "+541155551111");
        asignarAvisos(1, contacto, "EMAIL").andExpect(status().isOk());
        mvc.perform(put("/api/configuracion/contactos/" + contacto).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Contacto","email":"","telefono":"+541155551111"}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("artículo 1")));
        mvc.perform(get("/api/configuracion/contactos/" + contacto)).andExpect(jsonPath("$.email").value("original@contacto.example"));
        asignarAvisos(1, contacto, "SMS").andExpect(status().isOk());
        mvc.perform(put("/api/configuracion/contactos/" + contacto).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Contacto","email":"","telefono":"+541155551111"}
                """)).andExpect(status().isOk());
    }
    @Test void avisosPendientesConservanElContactoAunqueSeEditeElContacto() throws Exception {
        long contacto = crearContacto("Contacto", "original@contacto.example", "+541155551111");
        asignarAvisos(1, contacto, "EMAIL").andExpect(status().isOk());
        mensajes.setDisponible(false);
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        mvc.perform(put("/api/configuracion/contactos/" + contacto).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Contacto","email":"nuevo@contacto.example","telefono":"+541155551111"}
                """)).andExpect(status().isOk());
        asignarAvisos(1, contacto, "WHATSAPP").andExpect(status().isOk());
        mensajes.setDisponible(true);
        enviar("/api/integraciones/reintentos", "").andExpect(status().isOk());
        assertEquals("original@contacto.example", mensajes.getMensajes().getFirst().to());
        assertEquals("EMAIL", mensajes.getMensajes().getFirst().canal());
        enviar("/api/recepcion/ingresos", operacion(1, 61)).andExpect(status().isCreated());
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        assertEquals("WHATSAPP", mensajes.getMensajes().getLast().canal());
        assertEquals("+541155551111", mensajes.getMensajes().getLast().to());
    }
    @Test void quitarContactoVuelveAlDestinatarioGeneralSinBorrarElContacto() throws Exception {
        long contacto = crearContacto("Contacto", "aviso@contacto.example", "");
        asignarAvisos(1, contacto, "EMAIL").andExpect(status().isOk());
        asignarAvisos(1, null, null).andExpect(status().isOk()).andExpect(jsonPath("$.contactoId").value(nullValue()));
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        assertEquals("compras@coto.example", mensajes.getMensajes().getFirst().to());
        mvc.perform(get("/api/configuracion/contactos")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test void contactoMantieneCompatibilidadConLaAgendaYAsignacionesAnteriores() throws Exception {
        long contacto = id(enviar("/api/configuracion/proveedores", """
                {"nombre":"Compras de Electro","email":"electro@coto.example","telefono":""}
                """).andExpect(status().isCreated()).andExpect(header().string("Location", startsWith("/api/configuracion/contactos/"))).andReturn());
        mvc.perform(put("/api/productos/1/politica-reposicion").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipo":"PUNTO_PEDIDO","puntoPedido":20,"stockObjetivo":100,"demandaDiaria":0,"diasCobertura":1,
                 "factorEstacional":1,"umbralAlerta":20,"proveedorId":%d,"canalAlerta":"EMAIL"}
                """.formatted(contacto))).andExpect(status().isOk()).andExpect(jsonPath("$.contactoId").value(contacto));
        assertEquals(contacto, jdbc.queryForObject("SELECT proveedor_id FROM politica_reposicion WHERE stock_id=1", Long.class));
        mvc.perform(get("/api/configuracion/contactos")).andExpect(jsonPath("$[0].nombre").value("Compras de Electro"));
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        assertEquals("electro@coto.example", mensajes.getMensajes().getFirst().to());
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
        var creada = enviar("/api/categorias", "{\"nombre\":\"  Bazar  \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value("Bazar")).andReturn();
        String location = creada.getResponse().getHeader("Location");
        assertNotNull(location);
        mvc.perform(get(location)).andExpect(jsonPath("$.nombre").value("Bazar"));
        enviar("/api/categorias", "{\"nombre\":\"bazar\"}").andExpect(status().isBadRequest());
        configurarCategoria((int) id(creada), "Hogar", null, null).andExpect(status().isOk());
        assertEquals("Hogar", jdbc.queryForObject("SELECT nombre FROM categoria WHERE id=?", String.class, id(creada)));
        configurarCategoria((int) id(creada), "ELECTRO", null, null).andExpect(status().isBadRequest());
        mvc.perform(get(location)).andExpect(jsonPath("$.nombre").value("Hogar"));
    }
    @Test void altasDeArticulosYCombosAceptanCategoriaYRechazanReferenciasInvalidas() throws Exception {
        enviar("/api/productos", """
                {"nombre":"Licuadora","descripcion":"Electrodoméstico","precio":10000,"categoriaId":4}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.categoriaId").value(4));
        enviar("/api/combos", """
                {"nombre":"Combo limpieza","descripcion":"Prueba de clasificación","precio":100,"categoriaId":5,
                 "componentes":[{"stockId":1,"cantidad":1}]}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.categoriaId").value(5));
        enviar("/api/productos", """
                {"nombre":"Sin clasificar","descripcion":"Compatible con altas anteriores","precio":100}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.categoriaId").value(nullValue()));
        enviar("/api/productos", """
                {"nombre":"Inválido","descripcion":"Prueba","precio":100,"categoriaId":999}
                """).andExpect(status().isNotFound());
        enviar("/api/combos", """
                {"nombre":"Inválido","descripcion":"Prueba","precio":100,"categoriaId":999,
                 "componentes":[{"stockId":1,"cantidad":1}]}
                """).andExpect(status().isNotFound());
        assertEquals(9, jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class));
    }
    @Test void cambiarCategoriaPreservaStockHistorialYLaClasificacionDeComponentes() throws Exception {
        mvc.perform(put("/api/productos/6/categoria").contentType(MediaType.APPLICATION_JSON).content("{\"categoriaId\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoriaId").value(4)).andExpect(jsonPath("$.disponible").value(35));
        mvc.perform(get("/api/productos/1")).andExpect(jsonPath("$.categoriaId").value(1)).andExpect(jsonPath("$.stockFisico").value(120));
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
    @Test void avisosPriorizanArticuloLuegoCategoriaYLuegoSucursal() throws Exception {
        long almacen = crearContacto("Compras de Almacén", "almacen@coto.example", "");
        long particular = crearContacto("Responsable de aceite", "", "+541155553333");
        configurarCategoria(1, "Almacén", almacen, "EMAIL").andExpect(status().isOk());
        asignarAvisos(2, particular, "SMS").andExpect(status().isOk());
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        enviar("/api/digital/reservas", operacion(2, 26)).andExpect(status().isCreated());
        enviar("/api/digital/reservas", operacion(3, 1)).andExpect(status().isCreated());
        assertEquals(3, mensajes.getMensajes().size());
        assertEquals("almacen@coto.example", mensajes.getMensajes().get(0).to());
        assertEquals("+541155553333", mensajes.getMensajes().get(1).to());
        assertEquals("SMS", mensajes.getMensajes().get(1).canal());
        assertEquals("compras@coto.example", mensajes.getMensajes().get(2).to());
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
    @Test void cambiosDeCategoriaNoReescribenAvisosPendientes() throws Exception {
        long primero = crearContacto("Almacén", "primero@coto.example", "");
        long segundo = crearContacto("Reemplazo", "segundo@coto.example", "");
        configurarCategoria(1, "Almacén", primero, "EMAIL").andExpect(status().isOk());
        mensajes.setDisponible(false);
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        configurarCategoria(1, "Almacén", segundo, "EMAIL").andExpect(status().isOk());
        mensajes.setDisponible(true);
        enviar("/api/integraciones/reintentos", "").andExpect(status().isOk());
        assertEquals("primero@coto.example", mensajes.getMensajes().getFirst().to());
        enviar("/api/recepcion/ingresos", operacion(1, 61)).andExpect(status().isCreated());
        enviar("/api/digital/reservas", operacion(1, 61)).andExpect(status().isCreated());
        assertEquals("segundo@coto.example", mensajes.getMensajes().getLast().to());
    }

}
