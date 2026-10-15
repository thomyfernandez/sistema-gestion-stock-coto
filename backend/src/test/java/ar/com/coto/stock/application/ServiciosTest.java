package ar.com.coto.stock.application;

import ar.com.coto.stock.adapter.*;
import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.notificacion.*;
import ar.com.coto.stock.domain.observer.AlertaStockBajo;
import ar.com.coto.stock.domain.reposicion.*;
import ar.com.coto.stock.service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ServiciosTest {
    @Test void contextoReposicionCambiaEstrategiaSinModificarStock() {
        var stock = new StockSimple(new Producto(1, "Leche", "COTO", 100), 6, 0, 0);
        var servicio = new ServicioReposicion(new ReposicionPuntoDePedido(10, 50), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertEquals(44, servicio.generarPedido(stock).getCantidad());
        servicio.setEstrategia(new ReposicionPorDemanda(12, 3));
        assertEquals(30, servicio.generarPedido(stock).getCantidad());
        servicio.setEstrategia(new ReposicionEstacional(40, 1.5));
        assertEquals(54, servicio.generarPedido(stock).getCantidad());
        assertEquals(Instant.EPOCH, servicio.generarPedido(stock).getFecha().toInstant());
        assertTrue(servicio.planificar(stock, new ReposicionPorDemanda(12, 3), 30).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> servicio.generarPedido(new ComboStock(new Producto(2, "Combo", "Combo", 100))));
        assertEquals(6, stock.getDisponible());
    }

    @Test void observerServicioStrategyAdapterYAdapteeColaboranExplicitamente() {
        ApiProveedorExterno api = new ApiProveedorExterno();
        ServicioNotificaciones servicio = new ServicioNotificaciones(Map.of(
                ServicioNotificaciones.Canal.EMAIL, new EmailNotificador(new AdaptadorProveedor(api, "EMAIL")),
                ServicioNotificaciones.Canal.SMS, new SmsNotificador(new AdaptadorProveedorSMS(api)),
                ServicioNotificaciones.Canal.WHATSAPP, new WhatsAppNotificador(new AdaptadorProveedor(api, "WHATSAPP"))));
        StockSimple stock = new StockSimple(new Producto(1, "Arroz", "COTO", 100), 10, 0, 0);
        stock.agregarObservador(new AlertaStockBajo(10, "destino.demo", servicio));
        servicio.setCanal(ServicioNotificaciones.Canal.WHATSAPP);
        stock.reservar(1);
        assertEquals("WHATSAPP", api.getMensajes().getFirst().canal());
        assertEquals("destino.demo", api.getMensajes().getFirst().to());
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
