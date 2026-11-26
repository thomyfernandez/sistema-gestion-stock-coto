package ar.com.coto.stock.application;

import java.math.BigDecimal;
import ar.com.coto.stock.adapter.*;
import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.notificacion.*;
import ar.com.coto.stock.domain.observer.AlertaStockBajo;
import ar.com.coto.stock.domain.reposicion.*;
import ar.com.coto.stock.service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import ar.com.coto.stock.domain.port.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class ServiciosTest {
    @Test void contextoReposicionCambiaEstrategiaSinModificarStock() {
        var stock = new StockSimple(new Producto(1, "Leche", "COTO", new BigDecimal("100")), 6, 0, 0);
        var servicio = new ServicioReposicion(new ReposicionPuntoDePedido(10, 50), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertEquals(44, servicio.generarPedido(stock).getCantidad());
        servicio.setEstrategia(new ReposicionPorDemanda(12, 3));
        assertEquals(30, servicio.generarPedido(stock).getCantidad());
        servicio.setEstrategia(new ReposicionEstacional(40, 1.5));
        assertEquals(54, servicio.generarPedido(stock).getCantidad());
        assertEquals(Instant.EPOCH, servicio.generarPedido(stock).getFecha().toInstant());
        assertTrue(servicio.planificar(stock, new ReposicionPorDemanda(12, 3), 30).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> servicio.generarPedido(new ComboStock(new Producto(2, "Combo", "Combo", new BigDecimal("100")))));
        assertEquals(6, stock.getDisponible());
    }

    @Test void contextoArmaLaEstrategiaEstacionalConLasTemporadasDelArticuloYSuCategoria() {
        var stock = new StockSimple(new Producto(5, "Pan dulce", "COTO 500 g", new BigDecimal("5490"), null, 1), 30, 0, 0);
        var navidad = new Temporada(1, "Navidad y Reyes", java.util.List.of(5), null, MonthDay.of(11, 15), MonthDay.of(1, 6), new java.math.BigDecimal("1.5"));
        var invierno = new Temporada(2, "Invierno almacén", java.util.List.of(), 1, MonthDay.of(6, 1), MonthDay.of(8, 31), new java.math.BigDecimal("1.2"));
        var consultas = new java.util.ArrayList<String>();
        RepositorioTemporadas repositorio = new RepositorioTemporadas() {
            public java.util.List<Temporada> listar() { return java.util.List.of(navidad, invierno); }
            public java.util.List<Temporada> listarPara(int stockId, Integer categoriaId) { consultas.add(stockId + "/" + categoriaId); return listar(); }
            public java.util.Optional<Temporada> obtener(long id) { return java.util.Optional.empty(); }
            public Temporada guardar(Temporada t) { return t; }
            public void eliminar(long id) { }
        };
        var politica = new PoliticaReposicion(5, PoliticaReposicion.Tipo.ESTACIONAL, 0, 40, 0, 1, 10, "compras@coto.example");
        var zona = ZoneId.of("America/Argentina/Buenos_Aires");
        var diciembre = new ServicioReposicion(new ReposicionPuntoDePedido(10, 50), Clock.fixed(Instant.parse("2026-12-20T15:00:00Z"), zona), repositorio);
        assertEquals(30, diciembre.planificar(stock, diciembre.estrategiaPara(politica, stock), 0).orElseThrow().getCantidad());
        var julio = new ServicioReposicion(new ReposicionPuntoDePedido(10, 50), Clock.fixed(Instant.parse("2026-07-09T15:00:00Z"), zona), repositorio);
        assertEquals(18, julio.planificar(stock, julio.estrategiaPara(politica, stock), 0).orElseThrow().getCantidad()); // 48 − 30.
        var marzo = new ServicioReposicion(new ReposicionPuntoDePedido(10, 50), Clock.fixed(Instant.parse("2026-03-10T15:00:00Z"), zona), repositorio);
        assertEquals(5, marzo.planificar(stock, marzo.estrategiaPara(politica, stock), 5).orElseThrow().getCantidad()); // 40 − 30 − 5 pendientes.
        assertEquals(java.util.List.of("5/1", "5/1", "5/1"), consultas);
        // Las demás políticas no consultan temporadas.
        var puntoPedido = new PoliticaReposicion(5, PoliticaReposicion.Tipo.PUNTO_PEDIDO, 31, 40, 0, 1, 10, "compras@coto.example");
        assertEquals(10, marzo.estrategiaPara(puntoPedido, stock).calcularCantidad(stock));
        assertEquals(3, consultas.size());
    }

    @Test void observerServicioStrategyAdapterYAdapteeColaboranExplicitamente() {
        ApiProveedorExterno api = new ApiProveedorExterno();
        ServicioNotificaciones servicio = new ServicioNotificaciones(Map.of(
                Canal.EMAIL, new EmailNotificador(new AdaptadorProveedor(api, Canal.EMAIL)),
                Canal.SMS, new SmsNotificador(new AdaptadorProveedorSMS(api)),
                Canal.WHATSAPP, new WhatsAppNotificador(new AdaptadorProveedor(api, Canal.WHATSAPP))), new RepositorioAvisos() {
                    private ConfiguracionAvisos c = new ConfiguracionAvisos(Canal.EMAIL, "compras@coto.example", "+541100000000");
                    public ConfiguracionAvisos obtener() { return c; }
                    public void guardar(ConfiguracionAvisos nuevo) { c = nuevo; }
                }, new UnidadDeTrabajo() {
                    public <T> T ejecutar(Supplier<T> s) { return s.get(); }
                    public <T> T consultar(Supplier<T> s) { return s.get(); }
                    public <T> T ejecutarNueva(Supplier<T> s) { return s.get(); }
                    public void despuesDeConfirmar(Runnable r) { r.run(); }
                });
        StockSimple stock = new StockSimple(new Producto(1, "Arroz", "COTO", new BigDecimal("100")), 10, 0, 0);
        // En producción el emisor encola el aviso en la outbox; acá se entrega en el momento con el canal vigente.
        stock.agregarObservador(new AlertaStockBajo(10, alerta -> {
            var configuracion = servicio.consultarConfiguracion();
            servicio.enviar(configuracion.canal(), configuracion.destino(), alerta.getUltimoAviso().getTexto());
        }));
        servicio.setCanal(Canal.WHATSAPP);
        stock.reservar(1);
        assertEquals("WHATSAPP", api.getMensajes().getFirst().canal());
        assertEquals("+541100000000", api.getMensajes().getFirst().to());
    }

    @Test void adaptadorERPUtilizaReferenciaIdempotente() {
        SistemaERPExterno api = new SistemaERPExterno();
        AdaptadorERP adaptador = new AdaptadorERP(api);
        PedidoRegistrado pedido = new PedidoRegistrado(42, new PedidoReposicion(1, new java.util.Date(0), 10));
        adaptador.enviarPedido(pedido);
        adaptador.enviarPedido(pedido);
        assertEquals(1, api.getOrdenes().size());
        assertEquals("COTO-42", api.getOrdenes().getFirst().reference());
    }
}
