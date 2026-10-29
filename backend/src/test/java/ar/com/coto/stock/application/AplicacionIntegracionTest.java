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
        for (String tabla : List.of("imagen_producto", "configuracion_avisos", "evento_integracion", "promocion", "politica_reposicion", "auditoria_stock", "pedido_reposicion",
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
        assertEquals(1, contar("evento_integracion"));
        assertTrue(digital.getInventario().isEmpty());
        assertTrue(erp.getOrdenes().isEmpty());
    }

    @Test void rollbackTardioRevierteContadoresAuditoriaPedidosYOutboxSinEnviar() {
        assertThrows(IllegalStateException.class, () -> unidad.ejecutar(() -> {
            gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 61));
            throw new IllegalStateException("Abortar después de guardar toda la operación");
        }));
        assertEquals(80, gestor.consultarStock(1).getDisponible());
        assertEquals(14, contar("movimiento_stock"));
        assertEquals(6, contar("auditoria_stock"));
        assertEquals(1, contar("pedido_reposicion"));
        assertEquals(1, contar("evento_integracion"));
        assertTrue(mensajes.getMensajes().isEmpty());
        assertTrue(digital.getInventario().isEmpty());
        assertTrue(erp.getOrdenes().isEmpty());
    }

    @Test void reposicionAutomaticaDescuentaPedidosAbiertosYNoLosDuplica() {
        var resultado = gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 61));
        assertEquals(1, resultado.pedidosGenerados().size());
        assertEquals(81, resultado.pedidosGenerados().getFirst().pedido().getCantidad());
        gestor.evaluarReposicion();
        gestor.evaluarReposicion();
        assertEquals(1, pedidosDe(1).size());
        assertEquals(EstadoPedido.ENVIADO, pedidosDe(1).getFirst().pedido().getEstado());
        assertEquals(1, pedidosDe(3).size()); // Las 30 leches iniciales cubren lo requerido.
        assertEquals(30, pedidosDe(5).getFirst().pedido().getCantidad()); // Estacional: 40*1.5-30.
        assertTrue(pedidosDe(4).isEmpty());
    }

    @Test void demandaPideSoloElFaltanteAdicionalFrenteAPedidosPendientes() {
        gestor.registrarMovimiento(movimiento(3, TipoMovimiento.RESERVA_DIGITAL, 1));
        assertEquals(List.of(30, 1), pedidosDe(3).stream().map(p -> p.pedido().getCantidad()).toList());
        gestor.evaluarReposicion();
        assertEquals(2, pedidosDe(3).size());
    }

    @Test void episodioDeAlertaSeConservaEntreTransaccionesYSeRearma() {
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 61)); // 19 < 20.
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 1)); // 18.
        assertEquals(1, mensajes.getMensajes().size());
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.CANCELACION_RESERVA, 10)); // 28.
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 10)); // 18.
        assertEquals(2, mensajes.getMensajes().size());
        assertTrue(jdbc.queryForObject("SELECT alerta_activa FROM politica_reposicion WHERE stock_id = 1", Boolean.class));
    }

    @Test void evaluacionInicialAlertaUnaVezAlStockYaBajo() {
        gestor.evaluarReposicion();
        gestor.evaluarReposicion();
        assertEquals(1, mensajes.getMensajes().size());
        assertTrue(mensajes.getMensajes().getFirst().body().contains("Leche"));
    }

    @Test void fallaDeProveedorNoRevierteStockYMensajeConservaCanalParaReintento() {
        gestor.configurarPolitica(new PoliticaReposicion(1, PoliticaReposicion.Tipo.PUNTO_PEDIDO, 20, 100, 0, 1, 1, 80, "5491100000000"));
        notificaciones.setCanal(ServicioNotificaciones.Canal.SMS);
        mensajes.setDisponible(false);
        assertDoesNotThrow(() -> gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 1)));
        assertEquals(79, gestor.consultarStock(1).getDisponible());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evento_integracion WHERE estado = 'PENDIENTE' AND tipo = 'ALERTA'", Integer.class));
        notificaciones.setCanal(ServicioNotificaciones.Canal.EMAIL);
        mensajes.setDisponible(true);
        integraciones.procesarPendientes();
        assertEquals("SMS", mensajes.getMensajes().getFirst().canal());
        assertEquals(2, jdbc.queryForObject("SELECT intentos FROM evento_integracion WHERE tipo = 'ALERTA'", Integer.class));
        assertEquals(0, integraciones.procesarPendientes());
        assertEquals(1, mensajes.getMensajes().size());
    }

    @Test void colaFifoNoPermiteQueUnSnapshotViejoSobrescribaUnoNuevo() {
        digital.setDisponible(false);
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 1));
        gestor.registrarMovimiento(movimiento(1, TipoMovimiento.RESERVA_DIGITAL, 1));
        assertEquals(78, gestor.consultarStock(1).getDisponible());
        assertTrue(digital.getInventario().isEmpty());
        digital.setDisponible(true);
        assertTrue(integraciones.procesarPendientes() >= 4);
        assertEquals("78", digital.getInventario().get("1"));
        assertEquals("35", digital.getInventario().get("6"));
    }

    @Test void erpCaidoDejaPedidoPendienteYLoEnviaAlRecuperarse() {
        erp.setDisponible(false);
        gestor.evaluarReposicion();
        assertEquals(EstadoPedido.PENDIENTE, pedidosDe(3).getFirst().pedido().getEstado());
        assertTrue(erp.getOrdenes().isEmpty());
        erp.setDisponible(true);
        integraciones.procesarPendientes();
        assertEquals(EstadoPedido.ENVIADO, pedidosDe(3).getFirst().pedido().getEstado());
        assertEquals(2, erp.getOrdenes().size());
        integraciones.procesarPendientes();
        assertEquals(2, erp.getOrdenes().size());
    }

    @Test void recepcionDePedidoEsAtomicaYNoPuedeDuplicarse() {
        integraciones.procesarPendientes();
        gestor.recibirPedido(1, "recepcion.demo");
        assertEquals(36, gestor.consultarStock(3).getDisponible());
        assertEquals(EstadoPedido.RECIBIDO, pedidosDe(3).getFirst().pedido().getEstado());
        assertThrows(IllegalStateException.class, () -> gestor.recibirPedido(1, "recepcion.demo"));
        assertEquals(36, gestor.consultarStock(3).getDisponible());
        assertEquals(15, contar("movimiento_stock"));
    }

    @Test void noSeRecibeUnPedidoTodaviaNoEnviado() {
        assertThrows(IllegalStateException.class, () -> gestor.recibirPedido(1, "demo"));
        assertEquals(6, gestor.consultarStock(3).getDisponible());
    }

    @Test void recepcionFuncionaDespuesDeRenovarLasConexionesDelPool() throws Exception {
        integraciones.procesarPendientes();
        dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getHikariPoolMXBean().softEvictConnections();

        var resultado = gestor.recibirPedido(1, "recepcion.demo");
        assertEquals(36, resultado.disponibilidades().get(3));
        assertEquals(36, gestor.consultarStock(3).getDisponible());
        assertEquals(EstadoPedido.RECIBIDO, pedidosDe(3).getFirst().pedido().getEstado());
        assertEquals(15, contar("movimiento_stock"));
        assertThrows(IllegalStateException.class, () -> gestor.recibirPedido(1, "recepcion.demo"));
        assertEquals(15, contar("movimiento_stock"));
    }

    @Test void pedidoCanceladoNoSeEnviaAlERP() {
        gestor.cancelarPedido(1);
        integraciones.procesarPendientes();
        assertTrue(erp.getOrdenes().isEmpty());
        assertEquals(EstadoPedido.CANCELADO, pedidosDe(3).getFirst().pedido().getEstado());
    }

    @Test void altaSimpleComienzaSinExistenciasYSoloElGestorLasIncrementa() {
        Stock nuevo = gestor.crearProducto(new AltaProducto("Yerba COTO", "Paquete de 1 kg", 3500));
        assertTrue(nuevo.getProducto().getId() >= 1000);
        assertEquals(0, nuevo.getDisponible());
        gestor.registrarMovimiento(movimiento(nuevo.getProducto().getId(), TipoMovimiento.RECEPCION, 20));
        assertEquals(20, gestor.consultarStock(nuevo.getProducto().getId()).getDisponible());
        gestor.configurarPolitica(new PoliticaReposicion(nuevo.getProducto().getId(), PoliticaReposicion.Tipo.PUNTO_PEDIDO,
                5, 30, 0, 1, 1, 5, "compras@coto.example"));
        gestor.registrarMovimiento(movimiento(nuevo.getProducto().getId(), TipoMovimiento.RESERVA_DIGITAL, 16));
        assertEquals(26, pedidosDe(nuevo.getProducto().getId()).getFirst().pedido().getCantidad());
    }

    @Test void comboAnidadoCompartidoSePersisteYReservaSinDuplicarIdentidades() {
        ComboStock nuevo = gestor.crearCombo(new AltaProducto("Canasta COTO", "Combo más arroz", 8000),
                List.of(new ComponenteCombo(6, 1), new ComponenteCombo(1, 1)));
        assertEquals(26, nuevo.getDisponible());
        gestor.registrarMovimiento(movimiento(nuevo.getProducto().getId(), TipoMovimiento.RESERVA_DIGITAL, 2));
        assertEquals(74, gestor.consultarStock(1).getDisponible());
        assertEquals(33, gestor.consultarStock(2).getDisponible());
        assertEquals(24, gestor.consultarStock(nuevo.getProducto().getId()).getDisponible());
    }

    @Test void altaDeComboInvalidoRevierteInclusoElProductoYNoPublica() {
        assertThrows(EntidadNoEncontradaException.class, () -> gestor.crearCombo(new AltaProducto("Inválido", "No existe componente", 100),
                List.of(new ComponenteCombo(999, 1))));
        assertEquals(6, contar("producto"));
        assertEquals(6, contar("stock"));
        assertEquals(1, contar("evento_integracion"));
        assertTrue(digital.getInventario().isEmpty());
    }

    @Test void comboNoRecibeMercaderiaFisicaNiPoliticaDeReposicion() {
        assertThrows(IllegalArgumentException.class, () -> gestor.registrarMovimiento(movimiento(6, TipoMovimiento.RECEPCION, 1)));
        assertThrows(IllegalArgumentException.class, () -> gestor.configurarPolitica(new PoliticaReposicion(6,
                PoliticaReposicion.Tipo.PUNTO_PEDIDO, 5, 20, 0, 1, 1, 5, "demo")));
        assertEquals(35, gestor.consultarStock(6).getDisponible());
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
