package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ComboStockPropioTest {
    private Producto prodArroz;
    private Producto prodAceite;
    private Producto prodCombo;
    private StockSimple arroz;
    private StockSimple aceite;
    private ComboStock combo;

    @BeforeEach
    void setup() {
        prodArroz = new Producto(1, "Arroz 1kg", "Almacén", 1500.0);
        prodAceite = new Producto(2, "Aceite 900ml", "Almacén", 2500.0);
        prodCombo = new Producto(6, "Canasta Ahorro", "Combo", 5000.0);

        // 20 arroces, 10 aceites
        arroz = new StockSimple(prodArroz, 20, 0, 0);
        aceite = new StockSimple(prodAceite, 10, 0, 0);

        // Combo necesita 2 arroces + 1 aceite
        combo = new ComboStock(prodCombo);
        combo.agregar(arroz, 2);
        combo.agregar(aceite, 1);
    }

    @Test
    @DisplayName("Inicialmente el combo tiene 0 stock propio y disponible puramente virtual")
    void estadoInicial() {
        assertEquals(0, combo.getStockFisico());
        assertEquals(0, combo.getDisponiblePropio());
        // Virtual: min(20/2, 10/1) = 10
        assertEquals(10, combo.getDisponibleVirtual());
        assertEquals(10, combo.getDisponible());
    }

    @Test
    @DisplayName("Armar 3 combos deduce componentes y genera stock físico propio")
    void armarCombosExitoso() {
        AtomicInteger avisosArroz = new AtomicInteger();
        arroz.agregarObservador(s -> avisosArroz.incrementAndGet());

        combo.armar(3);

        // Stock físico del combo incrementado
        assertEquals(3, combo.getStockFisico());
        assertEquals(3, combo.getDisponiblePropio());

        // Componentes reducidos: arroz consumió 3 * 2 = 6; aceite consumió 3 * 1 = 3
        assertEquals(14, arroz.getStockFisico());
        assertEquals(7, aceite.getStockFisico());

        // Virtual remanente: min(14/2, 7/1) = 7
        assertEquals(7, combo.getDisponibleVirtual());
        // Total disponible: 3 propio + 7 virtual = 10
        assertEquals(10, combo.getDisponible());
        assertTrue(avisosArroz.get() > 0, "Debe notificar a los observadores de los componentes");
    }

    @Test
    @DisplayName("Armar más combos de los posibles tira StockInsuficienteException y no muta componentes")
    void armarSinStockSuficienteFalla() {
        // Pedimos armar 11 (requiere 22 arroces y solo hay 20)
        assertThrows(StockInsuficienteException.class, () -> combo.armar(11));

        // Todo queda intacto
        assertEquals(20, arroz.getStockFisico());
        assertEquals(10, aceite.getStockFisico());
        assertEquals(0, combo.getStockFisico());
    }

    @Test
    @DisplayName("Desarmar combos restituye stock físico a los componentes")
    void desarmarCombosExitoso() {
        combo.armar(4);
        assertEquals(4, combo.getStockFisico());
        assertEquals(12, arroz.getStockFisico());
        assertEquals(6, aceite.getStockFisico());

        // Desarmamos 2
        combo.desarmar(2);

        assertEquals(2, combo.getStockFisico());
        assertEquals(2, combo.getDisponiblePropio());
        // Restituye 2 * 2 = 4 arroces -> 12 + 4 = 16
        assertEquals(16, arroz.getStockFisico());
        // Restituye 2 * 1 = 2 aceites -> 6 + 2 = 8
        assertEquals(8, aceite.getStockFisico());

        // Virtual: min(16/2, 8/1) = 8
        assertEquals(8, combo.getDisponibleVirtual());
        // Total: 2 + 8 = 10
        assertEquals(10, combo.getDisponible());
    }

    @Test
    @DisplayName("Desarmar más combos de los armados propios tira StockInsuficienteException")
    void desarmarSinStockPropioFalla() {
        combo.armar(2);
        assertThrows(StockInsuficienteException.class, () -> combo.desarmar(3));

        assertEquals(2, combo.getStockFisico());
    }

    @Test
    @DisplayName("Estrategia default ReservaFisicoPrimero consume primero el stock armado")
    void reservaFisicoPrimeroDefault() {
        combo.armar(3);
        // Tenemos: propio = 3, virtual = 7. Total = 10.
        // Reservamos 4 combos
        combo.reservar(4);

        // Los 3 propios quedaron reservados
        assertEquals(3, combo.getStockReservado());
        assertEquals(0, combo.getDisponiblePropio());

        // El 1 restante se reservó de los componentes virtuales (2 arroces y 1 aceite reservados)
        assertEquals(2, arroz.getStockReservado());
        assertEquals(1, aceite.getStockReservado());

        // Disponible virtual ahora: min((14-2)/2, (7-1)/1) = 6
        assertEquals(6, combo.getDisponibleVirtual());
        // Disponible total: 0 + 6 = 6
        assertEquals(6, combo.getDisponible());
    }

    @Test
    @DisplayName("Estrategia ReservaVirtualPrimero reserva primero de componentes virtuales")
    void reservaVirtualPrimero() {
        combo.setEstrategiaReserva(new ReservaVirtualPrimero());
        combo.armar(3);
        // Propio = 3, virtual = 7. Total = 10.
        // Reservamos 4 combos
        combo.reservar(4);

        // Los 4 se reservaron de virtual: 4 * 2 = 8 arroces, 4 * 1 = 4 aceites reservados
        assertEquals(0, combo.getStockReservado());
        assertEquals(3, combo.getDisponiblePropio()); // El propio quedó intacto

        assertEquals(8, arroz.getStockReservado());
        assertEquals(4, aceite.getStockReservado());

        // Virtual restante: min((14-8)/2, (7-4)/1) = 3
        assertEquals(3, combo.getDisponibleVirtual());
        // Total: 3 propio + 3 virtual = 6
        assertEquals(6, combo.getDisponible());
    }

    @Test
    @DisplayName("Despachar reserva combinada descuenta tanto propio como virtual")
    void despacharReservaCombinada() {
        combo.armar(2);
        // Propio = 2, Virtual = 8.
        combo.reservar(3); // Reserva 2 propios + 1 virtual

        assertEquals(2, combo.getStockReservado());
        assertEquals(2, arroz.getStockReservado());
        assertEquals(1, aceite.getStockReservado());

        // Despachamos los 3
        combo.despacharReserva(3);

        // Propios despachados: fisicoPropio pasó de 2 a 0, reservadoPropio a 0
        assertEquals(0, combo.getStockFisico());
        assertEquals(0, combo.getStockReservado());

        // Virtuales despachados: físico de componentes bajó por 1 combo (2 arroces, 1 aceite)
        assertEquals(0, arroz.getStockReservado());
        assertEquals(14, arroz.getStockFisico()); // 20 - 4 (del armado de 2) - 2 (del despacho de 1) = 14
        assertEquals(0, aceite.getStockReservado());
        assertEquals(7, aceite.getStockFisico()); // 10 - 2 (del armado de 2) - 1 (del despacho de 1) = 7
    }

    @Test
    @DisplayName("Liberar reserva combinada restituye reservas propias y virtuales")
    void liberarReservaCombinada() {
        combo.armar(2);
        combo.reservar(3); // 2 propio + 1 virtual

        combo.liberarReserva(3);

        assertEquals(0, combo.getStockReservado());
        assertEquals(2, combo.getDisponiblePropio());

        assertEquals(0, arroz.getStockReservado());
        assertEquals(0, aceite.getStockReservado());
        assertEquals(8, combo.getDisponibleVirtual());
        assertEquals(10, combo.getDisponible());
    }
}

