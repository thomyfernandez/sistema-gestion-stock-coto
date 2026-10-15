package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.Producto;
import ar.com.coto.stock.domain.valorizacion.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class DecoradoresTest {
    private Producto producto() { return new Producto(1, "Arroz COTO", "1 kg", 1000); }

    @Test void precioDeListaUnitarioYTotalDeLinea() {
        assertEquals(1000, new PrecioLista(producto()).getPrecio());
        assertEquals(3000, new PrecioLista(producto(), 3).getPrecio());
    }

    @Test void descuentosSonAcumulablesYMultiplicativos() {
        ItemValorizable precio = new DescuentoDecorator(
                new DescuentoDecorator(new PrecioLista(producto()), 10), 20);
        assertEquals(720, precio.getPrecio(), 0.001);
    }

    @ParameterizedTest
    @CsvSource({"1,1000", "2,1000", "3,2000", "4,2000", "5,3000"})
    void dosPorUnoRespetaParesYUnidadesImpares(int cantidad, double esperado) {
        ItemValorizable precio = new PromocionDecorator(new PrecioLista(producto(), cantidad), "2x1");
        assertEquals(esperado, precio.getPrecio(), 0.001);
        assertEquals(cantidad, precio.getCantidad());
    }

    @Test void combinaDosPorUnoDescuentoYMedioDePago() {
        ItemValorizable precio = new PromocionDecorator(
                new DescuentoDecorator(new PromocionDecorator(new PrecioLista(producto(), 3), "2x1"), 10),
                "Descuento Medio de Pago", 15);
        assertEquals(1530, precio.getPrecio(), 0.001); // 3000 -> 2000 -> 1800 -> 1530.
    }

    @Test void porcentajesExtremosValidosYProductoNoSeModifica() {
        Producto producto = producto();
        assertEquals(0, new DescuentoDecorator(new PrecioLista(producto), 100).getPrecio());
        assertEquals(1000, new DescuentoDecorator(new PrecioLista(producto), 0).getPrecio());
        assertEquals(1000, producto.getPrecio());
    }

    @Test void rechazaPreciosCantidadesYPromocionesInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> new Producto(1, "A", "B", -1));
        assertThrows(IllegalArgumentException.class, () -> new Producto(1, "A", "B", Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new PrecioLista(producto(), 0));
        assertThrows(IllegalArgumentException.class, () -> new DescuentoDecorator(new PrecioLista(producto()), 101));
        assertThrows(IllegalArgumentException.class, () -> new DescuentoDecorator(new PrecioLista(producto()), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new PromocionDecorator(new PrecioLista(producto()), "3x2"));
        assertThrows(IllegalArgumentException.class, () -> new PromocionDecorator(new PrecioLista(producto()), "Descuento Medio de Pago"));
        assertThrows(IllegalArgumentException.class, () -> new PromocionDecorator(new PrecioLista(producto()), "2x1", 10));
    }
}
