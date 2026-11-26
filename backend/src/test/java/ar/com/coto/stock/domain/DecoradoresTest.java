package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.Producto;
import ar.com.coto.stock.domain.valorizacion.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class DecoradoresTest {
    private Producto producto() { return new Producto(1, "Arroz COTO", "1 kg", 1000); }

    private static void assertImporte(String esperado, ItemValorizable item) {
        assertEquals(0, new BigDecimal(esperado).compareTo(item.getImporte()),
                () -> "Se esperaba " + esperado + " y se obtuvo " + item.getImporte());
    }

    @Test void precioDeListaUnitarioYTotalDeLinea() {
        assertImporte("1000", new PrecioLista(producto()));
        assertImporte("3000", new PrecioLista(producto(), 3));
    }

    @Test void descuentosSonAcumulablesYMultiplicativos() {
        ItemValorizable precio = new DescuentoDecorator(
                new DescuentoDecorator(new PrecioLista(producto()), 10), 20);
        assertImporte("720", precio);
    }

    @ParameterizedTest
    @CsvSource({"1,1000", "2,1000", "3,2000", "4,2000", "5,3000"})
    void dosPorUnoRespetaParesYUnidadesImpares(int cantidad, String esperado) {
        ItemValorizable precio = new DosPorUnoDecorator(new PrecioLista(producto(), cantidad));
        assertImporte(esperado, precio);
        assertEquals(cantidad, precio.getCantidad());
    }

    @Test void combinaDosPorUnoDescuentoYMedioDePago() {
        ItemValorizable precio = new MedioPagoDecorator(
                new DescuentoDecorator(new DosPorUnoDecorator(new PrecioLista(producto(), 3)), 10),
                15);
        assertImporte("1530", precio); // 3000 -> 2000 -> 1800 -> 1530.
    }

    @Test void preservaPrecisionDecimalSinRedondeoIntermedio() {
        Producto centavos = new Producto(1, "Fideos", "500 g", 10.05);
        ItemValorizable precio = new MedioPagoDecorator(
                new DescuentoDecorator(new DosPorUnoDecorator(new PrecioLista(centavos, 3)), 15),
                10);
        // 30.15 -> 20.10 -> 17.085 -> 15.3765 exacto en BigDecimal
        assertImporte("15.3765", precio);
    }

    @Test void porcentajesExtremosValidosYProductoNoSeModifica() {
        Producto producto = producto();
        assertImporte("0", new DescuentoDecorator(new PrecioLista(producto), 100));
        assertImporte("1000", new DescuentoDecorator(new PrecioLista(producto), 0));
        assertEquals(1000, producto.getPrecio());
    }

    @Test void rechazaPreciosCantidadesYPromocionesInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> new Producto(1, "A", "B", -1));
        assertThrows(IllegalArgumentException.class, () -> new Producto(1, "A", "B", Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new PrecioLista(producto(), 0));
        assertThrows(IllegalArgumentException.class, () -> new DescuentoDecorator(new PrecioLista(producto()), 101));
        assertThrows(IllegalArgumentException.class, () -> new DescuentoDecorator(new PrecioLista(producto()), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MedioPagoDecorator(new PrecioLista(producto()), 101));
        assertThrows(IllegalArgumentException.class, () -> new MedioPagoDecorator(new PrecioLista(producto()), -5));
    }
}
