package ar.com.coto.stock.application;

import ar.com.coto.stock.adapter.*;
import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.UnidadDeTrabajo;
import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import ar.com.coto.stock.domain.valorizacion.Promocion;
import ar.com.coto.stock.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "coto.integraciones.automaticas=false",
        "spring.datasource.url=jdbc:h2:mem:etapa2test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000"
})
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AplicacionIntegracionTest {
    @Autowired GestionInventario gestor;
    @Autowired GestorInventario singleton;
    @Autowired ServicioValorizacion precios;
    @Autowired ServicioNotificaciones notificaciones;
    @Autowired ServicioIntegraciones integraciones;
    @Autowired UnidadDeTrabajo unidad;
    @Autowired ApiProveedorExterno mensajes;
    @Autowired SistemaERPExterno erp;
    @Autowired ApiCotoDigitalExterna digital;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reiniciar() {
        for (String tabla : List.of("usuario", "imagen_producto", "configuracion_avisos", "destinatario_aviso", "evento_integracion", "promocion", "politica_reposicion", "auditoria_stock", "pedido_reposicion",
                "movimiento_stock", "linea_combo", "stock", "producto", "categoria", "proveedor", "bloqueo_operacion")) jdbc.update("DELETE FROM " + tabla);
        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
        mensajes.setDisponible(true); erp.setDisponible(true); digital.setDisponible(true);
        mensajes.limpiar(); erp.limpiar(); digital.limpiar();
        notificaciones.setCanal(ServicioNotificaciones.Canal.EMAIL);
    }
    private MovimientoStock movimiento(int id, TipoMovimiento tipo, int cantidad) {
        return new MovimientoStock(id, tipo, cantidad, Instant.parse("2026-10-15T17:00:00Z"), "Prueba de integración", "demo");
    }
    private int contar(String tabla) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class); }
    private List<PedidoRegistrado> pedidosDe(int stockId) {
        return gestor.listarPedidos().stream().filter(p -> p.pedido().getStockId() == stockId).toList();
    }

    @Test void springUtilizaExactamenteElSingletonClasico() throws Exception {
        assertSame(GestorInventario.getInstancia(), singleton);
        assertSame(singleton, gestor);
        assertTrue(Modifier.isPrivate(GestorInventario.class.getDeclaredConstructor().getModifiers()));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<GestorInventario>> futuros = new ArrayList<>();
            for (int i = 0; i < 50; i++) futuros.add(executor.submit(GestorInventario::getInstancia));
            for (var futuro : futuros) assertSame(singleton, futuro.get());
        }
    }

    @Test void cargaJpaReutilizaUnaInstanciaPorStockYNoExponeEntidadesJpa() {
        ComboStock combo = (ComboStock) gestor.consultarStock(6);
        assertEquals(35, combo.getDisponible());
        assertEquals(2, combo.getComponentes().size());
        assertInstanceOf(StockSimple.class, combo.getComponentes().getFirst().getComponente());
        StockSimple copia = (StockSimple) gestor.consultarStock(1);
        copia.reservar(1);
        assertEquals(80, gestor.consultarStock(1).getDisponible());
    }

    @Test void reservaComboPersisteHojasMovimientoAuditoriaYSincronizacion() {
        var resultado = gestor.registrarMovimiento(movimiento(6, TipoMovimiento.RESERVA_DIGITAL, 2));
        assertEquals(76, gestor.consultarStock(1).getDisponible());
        assertEquals(33, gestor.consultarStock(2).getDisponible());
        assertEquals(33, gestor.consultarStock(6).getDisponible());
        assertEquals(Map.of(1, 76, 2, 33, 6, 33), resultado.disponibilidades());
        assertEquals(15, contar("movimiento_stock"));
        assertEquals(9, contar("auditoria_stock"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria_stock WHERE movimiento_id = ?", Integer.class, resultado.id()));
        assertEquals("33", digital.getInventario().get("6"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evento_integracion WHERE estado = 'PENDIENTE'", Integer.class));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM stock WHERE id = 1", Long.class));
    }

    @Test void reservaFallidaNoPersisteNiEntregaEfectosExternos() {
        assertThrows(StockInsuficienteException.class, () -> gestor.registrarMovimiento(movimiento(6, TipoMovimiento.RESERVA_DIGITAL, 36)));
        assertEquals(80, gestor.consultarStock(1).getDisponible());
        assertEquals(35, gestor.consultarStock(6).getDisponible());
        assertEquals(14, contar("movimiento_stock"));
        assertEquals(6, contar("auditoria_stock"));
        assertTrue(mensajes.getMensajes().isEmpty());
    }

    @Test void recepcionDisparaEvaluacionAutomatica() {
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RECEPCION, 5));
        assertEquals(85, gestor.consultarStock(1).getDisponible());
        assertEquals(1, contar("pedido_reposicion"));
    }

    @Test void reposicionAutomaticaEmiteOrdenIdempotenteAlERP() {
        assertEquals(1, contar("pedido_reposicion"));
        assertEquals(1, erp.getOrdenes().size());
        gestor.evaluarReposicion();
        assertEquals(1, erp.getOrdenes().size());
        assertEquals("COTO-1", erp.getOrdenes().getFirst().reference());
    }

    @Test void reintentosDeIntegracionesFallidasSonExponencialesYFinitos() {
        erp.setDisponible(false);
        gestor.evaluarReposicion();
        gestor.configurarPolitica(new PoliticaReposicion(2, PoliticaReposicion.Tipo.PUNTO_PEDIDO, 40, 100, 0, 1, 1, 10));
        gestor.evaluarReposicion();
        List<PedidoRegistrado> pedidos = pedidosDe(2);
        assertFalse(pedidos.isEmpty());
        long pedidoId = pedidos.getFirst().id();
        for (int i = 0; i < 5; i++) {
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evento_integracion WHERE estado = 'PENDIENTE' AND tipo = 'ORDEN_COMPRA' AND referencia_id = ?", Integer.class, pedidoId));
            integraciones.procesarPendientes();
        }
        assertEquals("FALLIDO", jdbc.queryForObject("SELECT estado FROM evento_integracion WHERE tipo = 'ORDEN_COMPRA' AND referencia_id = ?", String.class, pedidoId));
        assertEquals(5, jdbc.queryForObject("SELECT intentos FROM evento_integracion WHERE tipo = 'ORDEN_COMPRA' AND referencia_id = ?", Integer.class, pedidoId));
    }

    @Test void creacionDeComboValidaComponentesYPublicaDigital() {
        var combo = (ComboStock) gestor.crearCombo(new AltaProducto("Nuevo Combo", "Desc", 4500),
                List.of(new ComponenteCombo(1, 2), new ComponenteCombo(2, 1)));
        assertEquals(35, combo.getDisponible());
        assertEquals(7, contar("producto"));
        assertEquals(7, contar("stock"));
        assertEquals(2, contar("linea_combo"));
        assertEquals("35", digital.getInventario().get(String.valueOf(combo.getId())));
    }

    @Test void errorEnCreacionDeComboNoPersisteNiPublicaDigital() {
        assertThrows(EntidadNoEncontradaException.class, () -> gestor.crearCombo(new AltaProducto("Inválido", "No existe componente", 100),
                List.of(new ComponenteCombo(999, 1))));
        assertEquals(6, contar("producto"));
        assertEquals(6, contar("stock"));
        assertEquals(1, contar("evento_integracion"));
        assertTrue(digital.getInventario().isEmpty());
    }

    @Test void armadoYDesarmadoDeComboPersisteStockFisicoYComponentes() {
        var resArmado = gestor.registrarMovimiento(movimiento(6, TipoMovimiento.ARMADO_COMBO, 5));
        assertEquals(35, resArmado.disponibilidades().get(6));
        assertEquals(70, resArmado.disponibilidades().get(1));
        assertEquals(30, resArmado.disponibilidades().get(2));

        ComboStock comboBD = (ComboStock) gestor.consultarStock(6);
        assertEquals(5, comboBD.getStockFisico());
        assertEquals(5, comboBD.getDisponiblePropio());
        assertEquals(30, comboBD.getDisponibleVirtual());
        assertEquals(35, comboBD.getDisponible());

        StockSimple arrozBD = (StockSimple) gestor.consultarStock(1);
        assertEquals(110, arrozBD.getStockFisico());

        var resDesarmado = gestor.registrarMovimiento(movimiento(6, TipoMovimiento.DESARMADO_COMBO, 2));
        assertEquals(35, resDesarmado.disponibilidades().get(6));

        ComboStock comboDesarmado = (ComboStock) gestor.consultarStock(6);
        assertEquals(3, comboDesarmado.getStockFisico());
        assertEquals(3, comboDesarmado.getDisponiblePropio());

        StockSimple arrozRestituido = (StockSimple) gestor.consultarStock(1);
        assertEquals(114, arrozRestituido.getStockFisico());
    }

    @Test void comboNoRecibeMercaderiaFisicaNiPoliticaDeReposicion() {
        assertThrows(IllegalArgumentException.class, () -> gestor.registrarMovimiento(movimiento(6, TipoMovimiento.RECEPCION, 1)));
        assertThrows(IllegalArgumentException.class, () -> gestor.configurarPolitica(new PoliticaReposicion(6,
                PoliticaReposicion.Tipo.PUNTO_PEDIDO, 5, 20, 0, 1, 1, 5)));
        assertEquals(35, gestor.consultarStock(6).getDisponible());
    }

    @Test void episodioDeAlertaSeConservaEntreTransaccionesYSeRearma() {
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 61)); // 19 < 20.
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 1)); // 18.
        assertEquals(2, mensajes.getMensajes().size()); // Lista general tiene 2 emails en data.sql
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.CANCELACION_RESERVA, 10)); // 28.
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 10)); // 18.
        assertEquals(4, mensajes.getMensajes().size()); // 2 nuevos avisos tras rearme
        assertTrue(jdbc.queryForObject("SELECT alerta_activa FROM politica_reposicion WHERE stock_id = 1", Boolean.class));
    }

    @Test void evaluacionInicialAlertaUnaVezAlStockYaBajo() {
        gestor.evaluarReposicion();
        gestor.evaluarReposicion();
        // El producto 3 (Leche en Frescos, cat 2) arranca en 6 < 10. Resuelve los 2 correos de Frescos.
        assertEquals(2, mensajes.getMensajes().size());
        assertTrue(mensajes.getMensajes().getFirst().body().contains("Leche"));
    }

    @Test void fallaDeProveedorNoRevierteStockYMensajeConservaCanalParaReintento() {
        gestor.configurarPolitica(new PoliticaReposicion(1, PoliticaReposicion.Tipo.PUNTO_PEDIDO, 20, 100, 0, 1, 1, 80));
        mensajes.setDisponible(false);
        assertDoesNotThrow(() -> gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 1)));
        assertEquals(79, gestor.consultarStock(1).getDisponible());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM evento_integracion WHERE estado = 'PENDIENTE' AND tipo = 'ALERTA'", Integer.class));
        mensajes.setDisponible(true);
        integraciones.procesarPendientes();
        assertEquals("EMAIL", mensajes.getMensajes().getFirst().canal());
        assertEquals(0, integraciones.procesarPendientes());
        assertEquals(2, mensajes.getMensajes().size());
    }

    @Test
    @DisplayName("CA5 — Artículo de categoría con 2+ emails encola un evento por cada dirección con canal EMAIL y claves únicas")
    void alertaPorCategoriaEncolaEventoPorCadaDestinatarioConClaveUnica() {
        // En data.sql, Frescos (cat 2) tiene frescos@coto.example y lacteos.sucursal@coto.com.ar.
        // Asignamos una política al producto 2 y lo cambiamos a categoría 2 para probar caída en movimiento.
        jdbc.update("UPDATE producto SET categoria_id = 2 WHERE id = 2");
        gestor.configurarPolitica(new PoliticaReposicion(2, PoliticaReposicion.Tipo.PUNTO_PEDIDO, 10, 50, 0, 1, 1, 10));

        mensajes.limpiar();
        // Stock disponible de 2 es 35. Reservamos 26 -> disponible pasa a 9 (< 10).
        gestor.registrarMovimiento(movimiento(2, TipoMovimiento.RESERVA_DIGITAL, 26));

        // Debe haber enviado 2 avisos (uno a cada destinatario de Frescos)
        assertEquals(2, mensajes.getMensajes().size());
        Set<String> destinos = new HashSet<>(List.of(mensajes.getMensajes().get(0).to(), mensajes.getMensajes().get(1).to()));
        assertTrue(destinos.contains("frescos@coto.example"));
        assertTrue(destinos.contains("lacteos.sucursal@coto.com.ar"));

        // Verificamos claves únicas en evento_integracion
        List<String> claves = jdbc.queryForList("SELECT clave FROM evento_integracion WHERE tipo = 'ALERTA' AND stock_id = 2", String.class);
        assertEquals(2, claves.size());
        assertEquals(2, new HashSet<>(claves).size()); // Claves distintas
        assertTrue(claves.stream().allMatch(c -> c.contains(":alerta:2:")));
    }

    @Test
    @DisplayName("CA6 — Artículo de categoría sin emails propios recurre a la lista general de la sucursal")
    void alertaSinEmailsEnCategoriaResuelveListaGeneral() {
        // Producto 1 (Arroz) está en Almacén (cat 1). Almacén no tiene emails en destinatario_aviso.
        mensajes.limpiar();
        // Disponible inicial 80, umbral 20. Reservamos 61 -> disponible 19 (< 20).
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 61));

        assertEquals(2, mensajes.getMensajes().size());
        Set<String> destinos = new HashSet<>(List.of(mensajes.getMensajes().get(0).to(), mensajes.getMensajes().get(1).to()));
        assertTrue(destinos.contains("compras@coto.example"));
        assertTrue(destinos.contains("sucursal42.stock@coto.com.ar"));
    }

    @Test
    @DisplayName("CA7 — Al descender aún más en el mismo episodio no duplica; al superar umbral y volver a caer emite nuevo lote")
    void alertaNoDuplicaEnMismoEpisodioYGeneraNuevoLoteAlRearmar() {
        mensajes.limpiar();
        // Primer movimiento: cae bajo umbral (80 -> 19)
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 61));
        assertEquals(2, mensajes.getMensajes().size());

        // Segundo movimiento dentro del mismo episodio (19 -> 15): no duplica
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 4));
        assertEquals(2, mensajes.getMensajes().size());

        // Liberación de reserva que supera el umbral (15 + 10 = 25 > 20): desactiva alerta y se rearma
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.CANCELACION_RESERVA, 10));
        assertFalse(jdbc.queryForObject("SELECT alerta_activa FROM politica_reposicion WHERE stock_id = 1", Boolean.class));

        // Nueva caída bajo umbral (25 -> 18 < 20): nuevo episodio, nuevo lote de alertas
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 7));
        assertEquals(4, mensajes.getMensajes().size());
        assertTrue(jdbc.queryForObject("SELECT alerta_activa FROM politica_reposicion WHERE stock_id = 1", Boolean.class));
    }

    @Test
    @DisplayName("Listas vacías (sin correos en categoría ni general): no encola eventos pero registra alerta_activa")
    void alertaSinDestinatariosNoGeneraEventosPeroActivaAlerta() {
        jdbc.update("DELETE FROM destinatario_aviso");
        mensajes.limpiar();

        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 61)); // 80 -> 19 < 20

        // No se encolan eventos de alerta
        assertEquals(0, mensajes.getMensajes().size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evento_integracion WHERE tipo = 'ALERTA'", Integer.class));
        // Pero la alerta queda registrada como activa en la política
        assertTrue(jdbc.queryForObject("SELECT alerta_activa FROM politica_reposicion WHERE stock_id = 1", Boolean.class));
    }

    @Test void colaFifoNoPermiteQueUnSnapshotViejoSobrescribaUnoNuevo() {
        digital.setDisponible(false);
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 1));
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 1));
        assertEquals(78, gestor.consultarStock(1).getDisponible());
        assertTrue(digital.getInventario().isEmpty());
        digital.setDisponible(true);
        assertEquals(2, integraciones.procesarPendientes());
        assertEquals("78", digital.getInventario().get("1"));
    }

    @Test void promocionesPersistidasSeAcumulanConFiltroDeMedioDePagoYRedondeoFinal() {
        var debito = precios.valorizar(1, 3, "DEBITO");
        assertEquals(new BigDecimal("5670.00"), debito.precioLista());
        assertEquals(new BigDecimal("2891.70"), debito.precioFinal());
        assertEquals(new BigDecimal("2778.30"), debito.ahorro());
        assertEquals(List.of(1L, 2L, 3L), debito.promocionesAplicadas());
        assertEquals(new BigDecimal("3402.00"), precios.valorizar(1, 3, "EFECTIVO").precioFinal());
        assertEquals(new BigDecimal("1701.00"), precios.valorizar(1, 1, null).precioFinal());
        assertEquals(1890, gestor.consultarStock(1).getProducto().getPrecio());
    }

    @Test void promocionesSeCreanYDesactivanYNoSeDuplicaElDosPorUno() {
        Promocion p = gestor.guardarPromocion(new Promocion(0, 2, Promocion.Tipo.DESCUENTO, 12.5, 1, true, null));
        assertEquals(new BigDecimal("2878.75"), precios.valorizar(2, 1, null).precioFinal());
        gestor.guardarPromocion(new Promocion(p.id(), 2, p.tipo(), p.porcentaje(), 1, false, null));
        assertEquals(new BigDecimal("3290.00"), precios.valorizar(2, 1, null).precioFinal());
        assertThrows(IllegalArgumentException.class, () -> gestor.guardarPromocion(new Promocion(0, 1, Promocion.Tipo.DOS_POR_UNO, 0, 40, true, null)));
    }

    @Test void unDescuentoFraccionarioSeRedondeaAlFinal() {
        gestor.guardarPromocion(new Promocion(0, 2, Promocion.Tipo.DESCUENTO, 33.3333, 1, true, null));
        assertEquals(new BigDecimal("2193.33"), precios.valorizar(2, 1, null).precioFinal());
    }

    @Test void reservasConcurrentesSeSerializanEnSqlYSinSobreventa() throws Exception {
        AtomicInteger exitos = new AtomicInteger();
        CountDownLatch inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futuros = new ArrayList<>();
            for (int i = 0; i < 45; i++) futuros.add(executor.submit(() -> {
                inicio.await();
                try { gestor.registrarMovimiento(movimiento(6, TipoMovimiento.RESERVA_DIGITAL, 1)); exitos.incrementAndGet(); }
                catch (StockInsuficienteException esperado) { /* Otras transacciones agotaron el combo. */ }
                return null;
            }));
            inicio.countDown();
            for (Future<?> futuro : futuros) futuro.get(30, TimeUnit.SECONDS);
        }
        assertEquals(35, exitos.get());
        assertEquals(0, gestor.consultarStock(6).getDisponible());
        assertEquals(10, gestor.consultarStock(1).getDisponible());
        assertEquals(0, gestor.consultarStock(2).getDisponible());
        assertEquals(49, contar("movimiento_stock"));
    }
}
