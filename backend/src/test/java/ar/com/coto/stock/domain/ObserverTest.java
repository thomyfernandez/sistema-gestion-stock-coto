package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.observer.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ObserverTest {
    private StockSimple stock() {
        return new StockSimple(new Producto(1, "Leche COTO", "1 litro", 1590), 20, 0, 0);
    }

    @Test void alertaSoloBajoUmbralYUnaVezPorEpisodio() {
        StockSimple stock = stock();
        List<AlertaStockBajo.Aviso> avisos = new ArrayList<>();
        AlertaStockBajo alerta = new AlertaStockBajo(10, "compras@coto.example", a -> avisos.add(a.getUltimoAviso()));
        stock.agregarObservador(alerta);
        stock.reservar(10); // Disponible 10: aún no está por debajo.
        assertTrue(avisos.isEmpty());
        stock.reservar(1); // 9: primer aviso.
        stock.reservar(1); // 8: mismo episodio.
        stock.liberarReserva(3); // 11: se rearma.
        stock.reservar(3); // 8: nuevo episodio.
        assertEquals(2, avisos.size());
        assertEquals(9, avisos.getFirst().disponible());
        assertEquals(8, avisos.getLast().disponible());
        assertTrue(avisos.getFirst().getTexto().contains("Leche COTO"));
    }

    @Test void fallaDeUnObserverNoInterrumpeLosDemasNiRevierteStock() {
        StockSimple stock = stock();
        AtomicInteger sincronizado = new AtomicInteger(-1);
        stock.agregarObservador(s -> { throw new IllegalStateException("Proveedor simulado caído"); });
        stock.agregarObservador(new SincronizadorCotoDigital((id, disponible) -> sincronizado.set(disponible)));
        assertDoesNotThrow(() -> stock.reservar(2));
        assertEquals(18, stock.getDisponible());
        assertEquals(18, sincronizado.get());
    }

    @Test void alertaFallidaPuedeReintentarse() {
        StockSimple stock = stock();
        AtomicInteger intentos = new AtomicInteger();
        AlertaStockBajo alerta = new AlertaStockBajo(10, "demo", a -> {
            if (intentos.incrementAndGet() == 1) throw new IllegalStateException("Falla simulada");
        });
        stock.agregarObservador(alerta);
        stock.reservar(11);
        stock.reservar(1);
        stock.reservar(1);
        assertEquals(2, intentos.get());
    }

    @Test void auditorRegistraSnapshotConRelojInyectado() {
        StockSimple stock = stock();
        List<AuditoriaStock> registros = new ArrayList<>();
        Instant ahora = Instant.parse("2026-10-15T12:00:00Z");
        stock.agregarObservador(new AuditorMovimientos(registros::add, Clock.fixed(ahora, ZoneOffset.UTC)));
        stock.reservar(3);
        stock.despacharReserva(2); // Disponible sigue 17, pero existe un cambio físico.
        assertEquals(2, registros.size());
        assertNull(registros.getFirst().disponibleAnterior());
        assertEquals(17, registros.getLast().disponibleAnterior());
        assertEquals(17, registros.getLast().disponibleActual());
        assertEquals(ahora, registros.getLast().fecha());
    }

    @Test void observadorNoSeDuplicaYPuedeDesuscribirse() {
        StockSimple stock = stock();
        AtomicInteger avisos = new AtomicInteger();
        ObservadorStock observador = s -> avisos.incrementAndGet();
        stock.agregarObservador(observador);
        stock.agregarObservador(observador);
        stock.reservar(1);
        stock.quitarObservador(observador);
        stock.reservar(1);
        assertEquals(1, avisos.get());
    }
}
