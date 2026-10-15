package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class MovimientosTest {
    private StockSimple stock() {
        return new StockSimple(new Producto(1, "Arroz COTO", "Paquete 1 kg", 1890), 100, 20, 10);
    }

    private MovimientoStock movimiento(TipoMovimiento tipo, int cantidad) {
        return new MovimientoStock(1, tipo, cantidad, Instant.parse("2026-10-15T12:00:00Z"), "Prueba", "pda.demo");
    }

    @ParameterizedTest
    @CsvSource({
        "RECEPCION,105,20,10,75", "DEVOLUCION,105,20,10,75",
        "TRASLADO_A_GONDOLA,100,25,10,65", "RETORNO_A_DEPOSITO,100,15,10,75",
        "MERMA_DEPOSITO,95,20,10,65", "MERMA_GONDOLA,95,15,10,70",
        "RESERVA_DIGITAL,100,20,15,65", "CANCELACION_RESERVA,100,20,5,75",
        "DESPACHO_DIGITAL,95,20,5,70"
    })
    void cadaMovimientoRespetaContadores(TipoMovimiento tipo, int fisico, int exhibicion, int reservado, int disponible) {
        StockSimple stock = stock();
        stock.aplicarMovimiento(movimiento(tipo, 5));
        assertAll(() -> assertEquals(fisico, stock.getStockFisico()),
                () -> assertEquals(exhibicion, stock.getStockExhibicion()),
                () -> assertEquals(reservado, stock.getStockReservado()),
                () -> assertEquals(disponible, stock.getDisponible()));
    }

    @ParameterizedTest
    @CsvSource({"TRASLADO_A_GONDOLA,71", "MERMA_DEPOSITO,71", "RETORNO_A_DEPOSITO,21",
            "MERMA_GONDOLA,21", "RESERVA_DIGITAL,71", "CANCELACION_RESERVA,11", "DESPACHO_DIGITAL,11"})
    void operacionInvalidaNoModificaContadores(TipoMovimiento tipo, int cantidad) {
        StockSimple stock = stock();
        assertThrows(StockInsuficienteException.class, () -> stock.aplicarMovimiento(movimiento(tipo, cantidad)));
        assertEquals(100, stock.getStockFisico());
        assertEquals(20, stock.getStockExhibicion());
        assertEquals(10, stock.getStockReservado());
    }

    @Test void validaIdentidadCantidadYEstadoInicial() {
        assertThrows(IllegalArgumentException.class, () -> stock().reservar(0));
        assertThrows(IllegalArgumentException.class, () -> stock().reservar(-1));
        assertThrows(IllegalArgumentException.class, () -> new StockSimple(stock().getProducto(), 5, 4, 2));
        assertThrows(IllegalArgumentException.class, () -> new StockSimple(stock().getProducto(), -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> stock().aplicarMovimiento(
                new MovimientoStock(2, TipoMovimiento.RECEPCION, 1, Instant.now(), "Prueba", "demo")));
        assertThrows(IllegalArgumentException.class, () -> movimiento(TipoMovimiento.RECEPCION, 0));
    }

    @Test void recepcionNoDesborda() {
        StockSimple stock = new StockSimple(stock().getProducto(), Integer.MAX_VALUE, 0, 0);
        assertThrows(ArithmeticException.class, () -> stock.aplicarMovimiento(movimiento(TipoMovimiento.RECEPCION, 1)));
        assertEquals(Integer.MAX_VALUE, stock.getStockFisico());
    }
}
