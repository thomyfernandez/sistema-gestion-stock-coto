package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.reposicion.*;
import ar.com.coto.stock.domain.notificacion.*;
import ar.com.coto.stock.domain.port.ProveedorMensajeria;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EstrategiasTest {
    private StockSimple stock(int disponible) {
        return new StockSimple(new Producto(1, "Leche", "COTO 1 l", 1590), disponible, 0, 0);
    }

    @Test void puntoDePedidoIncluyeElUmbral() {
        EstrategiaReposicion estrategia = new ReposicionPuntoDePedido(10, 50);
        assertEquals(44, estrategia.calcularCantidad(stock(6)));
        assertEquals(40, estrategia.calcularCantidad(stock(10)));
        assertEquals(0, estrategia.calcularCantidad(stock(11)));
    }

    @Test void demandaRedondeaHaciaArribaYSustraeDisponible() {
        EstrategiaReposicion estrategia = new ReposicionPorDemanda(2.5, 3);
        assertEquals(2, estrategia.calcularCantidad(stock(6))); // ceil(7.5) - 6.
        assertEquals(0, estrategia.calcularCantidad(stock(10)));
        assertEquals(30, new ReposicionPorDemanda(12, 3).calcularCantidad(stock(6)));
    }

    @Test void estacionalAjustaObjetivo() {
        EstrategiaReposicion estrategia = new ReposicionEstacional(40, 1.5);
        assertEquals(30, estrategia.calcularCantidad(stock(30)));
        assertEquals(0, estrategia.calcularCantidad(stock(70)));
        assertEquals(5, new ReposicionEstacional(3, 1.5).calcularCantidad(stock(0)));
    }

    @Test void validaConfiguracionesYDesbordes() {
        assertThrows(IllegalArgumentException.class, () -> new ReposicionPuntoDePedido(10, 10));
        assertThrows(IllegalArgumentException.class, () -> new ReposicionPorDemanda(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> new ReposicionPorDemanda(Double.NaN, 3));
        assertThrows(IllegalArgumentException.class, () -> new ReposicionPorDemanda(10, 0));
        assertThrows(IllegalArgumentException.class, () -> new ReposicionEstacional(10, 0));
        assertThrows(ArithmeticException.class, () -> new ReposicionEstacional(Integer.MAX_VALUE, 2));
        assertThrows(ArithmeticException.class, () -> new ReposicionPorDemanda(Double.MAX_VALUE, 3));
    }

    @Test void losTresCanalesSonIntercambiablesYDeleganEnSuProveedor() {
        List<String> enviados = new ArrayList<>();
        ProveedorMensajeria proveedor = (destino, texto) -> enviados.add(destino + ": " + texto);
        List<CanalNotificador> canales = List.of(new EmailNotificador(proveedor),
                new SmsNotificador(proveedor), new WhatsAppNotificador(proveedor));
        for (CanalNotificador canal : canales) {
            canal.enviar(" destino ", " Stock bajo ");
            assertThrows(IllegalArgumentException.class, () -> canal.enviar(" ", "Mensaje"));
        }
        assertEquals(List.of("destino: Stock bajo", "destino: Stock bajo", "destino: Stock bajo"), enviados);
    }

    @Test void pedidoTieneCantidadPositivaFechaDefensivaYTransicionesValidas() {
        Date fecha = new Date(1000);
        PedidoReposicion pedido = new PedidoReposicion(1, fecha, 30);
        fecha.setTime(2000);
        pedido.getFecha().setTime(3000);
        assertEquals(1000, pedido.getFecha().getTime());
        assertThrows(IllegalStateException.class, pedido::marcarRecibido);
        pedido.marcarEnviado();
        assertThrows(IllegalStateException.class, pedido::cancelar);
        pedido.marcarRecibido();
        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertThrows(IllegalArgumentException.class, () -> new PedidoReposicion(1, fecha, 0));
        PedidoReposicion cancelado = new PedidoReposicion(1, fecha, 1);
        cancelado.cancelar();
        assertThrows(IllegalStateException.class, cancelado::marcarEnviado);
    }
}
