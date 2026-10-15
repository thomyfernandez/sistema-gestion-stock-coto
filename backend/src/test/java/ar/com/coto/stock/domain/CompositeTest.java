package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CompositeTest {
    private Producto producto(int id) { return new Producto(id, "Producto " + id, "Prueba", 100); }
    private StockSimple simple(int id, int cantidad) { return new StockSimple(producto(id), cantidad, 0, 0); }

    @Test void disponibleSeCalculaSinCampoPropio() {
        StockSimple stock = new StockSimple(producto(1), 120, 30, 10);
        assertEquals(80, stock.getDisponible());
        stock.reservar(5);
        assertEquals(75, stock.getDisponible());
        assertThrows(NoSuchFieldException.class, () -> StockSimple.class.getDeclaredField("disponible"));
    }

    @Test void comboUsaMinimoConDivisionEntera() {
        StockSimple arroz = simple(1, 81);
        StockSimple aceite = simple(2, 35);
        ComboStock combo = new ComboStock(producto(6));
        combo.agregar(arroz, 2);
        combo.agregar(aceite, 1);
        assertEquals(35, combo.getDisponible());
        combo.reservar(3);
        assertAll(() -> assertEquals(6, arroz.getStockReservado()),
                () -> assertEquals(3, aceite.getStockReservado()),
                () -> assertEquals(32, combo.getDisponible()));
    }

    @Test void reservaFallidaNoModificaNingunComponenteNiNotifica() {
        StockSimple arroz = simple(1, 10);
        StockSimple aceite = simple(2, 1);
        ComboStock combo = new ComboStock(producto(6));
        combo.agregar(arroz, 2);
        combo.agregar(aceite, 1);
        AtomicInteger avisos = new AtomicInteger();
        arroz.agregarObservador(s -> avisos.incrementAndGet());
        assertThrows(StockInsuficienteException.class, () -> combo.reservar(2));
        assertEquals(0, arroz.getStockReservado());
        assertEquals(0, aceite.getStockReservado());
        assertEquals(0, avisos.get());
    }

    @Test void anidamientoConComponenteCompartidoSumaNecesidadesAntesDeDividir() {
        StockSimple arroz = simple(1, 10);
        ComboStock interior = new ComboStock(producto(6));
        interior.agregar(arroz, 2);
        ComboStock exterior = new ComboStock(producto(7));
        exterior.agregar(interior, 2);
        exterior.agregar(arroz, 1); // 2*2+1 = 5 unidades de arroz por combo.
        assertEquals(2, exterior.getDisponible());
        exterior.reservar(2);
        assertEquals(10, arroz.getStockReservado());
        assertEquals(0, exterior.getDisponible());
    }

    @Test void todosLosObservadoresVenElEstadoCompletoDelCombo() {
        StockSimple a = simple(1, 10);
        StockSimple b = simple(2, 10);
        ComboStock combo = new ComboStock(producto(6));
        combo.agregar(a, 1);
        combo.agregar(b, 1);
        List<Integer> reservasDeB = new ArrayList<>();
        a.agregarObservador(s -> reservasDeB.add(b.getStockReservado()));
        combo.reservar(3);
        assertEquals(List.of(3), reservasDeB);
    }

    @Test void cambiosEnHojaSePropaganAlComboPadre() {
        StockSimple a = simple(1, 10);
        ComboStock hijo = new ComboStock(producto(6));
        hijo.agregar(a, 2);
        ComboStock padre = new ComboStock(producto(7));
        padre.agregar(hijo, 1);
        List<Integer> catalogo = new ArrayList<>();
        padre.agregarObservador(s -> catalogo.add(s.getDisponible()));
        a.reservar(4);
        assertEquals(List.of(3), catalogo);
    }

    @Test void rechazaCiclosDirectosEIndirectos() {
        ComboStock a = new ComboStock(producto(6));
        ComboStock b = new ComboStock(producto(7));
        assertThrows(IllegalArgumentException.class, () -> a.agregar(a, 1));
        a.agregar(b, 1);
        assertThrows(IllegalArgumentException.class, () -> b.agregar(a, 1));
        assertTrue(b.getComponentes().isEmpty());
    }

    @Test void comboVacioONodoVacioNoSonVendibles() {
        ComboStock vacio = new ComboStock(producto(6));
        assertEquals(0, vacio.getDisponible());
        assertThrows(IllegalStateException.class, () -> vacio.reservar(1));
        ComboStock padre = new ComboStock(producto(7));
        padre.agregar(simple(1, 10), 1);
        padre.agregar(vacio, 1);
        assertEquals(0, padre.getDisponible());
        assertThrows(IllegalStateException.class, () -> padre.reservar(1));
    }

    @Test void rechazaDuplicadosYRepresentacionesDistintasDelMismoStock() {
        ComboStock hijo = new ComboStock(producto(6));
        hijo.agregar(simple(1, 10), 1);
        assertThrows(IllegalArgumentException.class, () -> hijo.agregar(simple(1, 10), 1));
        ComboStock padre = new ComboStock(producto(7));
        padre.agregar(hijo, 1);
        assertThrows(IllegalArgumentException.class, () -> padre.agregar(simple(1, 10), 1));
    }

    @Test void componentesNoSonModificablesDesdeAfuera() {
        ComboStock combo = new ComboStock(producto(6));
        combo.agregar(simple(1, 10), 1);
        assertThrows(UnsupportedOperationException.class, () -> combo.getComponentes().clear());
    }

    @Test void cambiarHijoNoPuedeIntroducirOtraInstanciaDeUnaIdentidadDelPadre() {
        StockSimple a = simple(1, 10);
        ComboStock hijo = new ComboStock(producto(6));
        ComboStock padre = new ComboStock(producto(7));
        padre.agregar(a, 1);
        padre.agregar(hijo, 1);
        assertThrows(IllegalArgumentException.class, () -> hijo.agregar(simple(1, 10), 1));
        assertTrue(hijo.getComponentes().isEmpty());
        hijo.agregar(a, 1); // Reutilizar la instancia correcta sí es válido.
        assertEquals(5, padre.getDisponible());
    }

    @Test void cambiarHijoNoPuedeDesbordarLasNecesidadesDelPadre() {
        ComboStock hijo = new ComboStock(producto(6));
        ComboStock padre = new ComboStock(producto(7));
        padre.agregar(hijo, 2);
        assertThrows(ArithmeticException.class, () -> hijo.agregar(simple(1, 10), Integer.MAX_VALUE));
        assertTrue(hijo.getComponentes().isEmpty());
        assertEquals(0, padre.getDisponible());
    }

    @Test void cancelarYDespacharOperanProporcionalmenteSinCambiarDisponibleAlDespachar() {
        StockSimple a = simple(1, 20);
        ComboStock combo = new ComboStock(producto(6));
        combo.agregar(a, 2);
        combo.reservar(4);
        combo.liberarReserva(1);
        assertEquals(6, a.getStockReservado());
        combo.despacharReserva(3);
        assertAll(() -> assertEquals(14, a.getStockFisico()),
                () -> assertEquals(0, a.getStockReservado()),
                () -> assertEquals(7, combo.getDisponible()));
    }

    @Test void rechazaSobreflujoSinReservaParcial() {
        StockSimple a = simple(1, Integer.MAX_VALUE);
        ComboStock combo = new ComboStock(producto(6));
        combo.agregar(a, 2);
        assertThrows(ArithmeticException.class, () -> combo.reservar(Integer.MAX_VALUE));
        assertEquals(0, a.getStockReservado());
    }

    @Test void reservasConcurrentesDeCombosNoSobrevenden() throws Exception {
        StockSimple a = simple(1, 40);
        StockSimple b = simple(2, 20);
        ComboStock combo = new ComboStock(producto(6));
        combo.agregar(a, 2);
        combo.agregar(b, 1);
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futuros = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futuros.add(executor.submit(() -> {
                    inicio.await();
                    try { combo.reservar(1); exitos.incrementAndGet(); }
                    catch (StockInsuficienteException esperado) { /* Se agotó el stock. */ }
                    return null;
                }));
            }
            inicio.countDown();
            for (Future<?> futuro : futuros) futuro.get();
        }
        assertEquals(20, exitos.get());
        assertEquals(40, a.getStockReservado());
        assertEquals(20, b.getStockReservado());
        assertEquals(0, combo.getDisponible());
    }
}
