package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.*;
import ar.com.coto.stock.domain.valorizacion.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class ServicioValorizacion implements ConsultaPrecios {
    private final RepositorioStock stocks;
    private final RepositorioPromociones promociones;
    private final UnidadDeTrabajo unidad;
    public ServicioValorizacion(RepositorioStock stocks, RepositorioPromociones promociones, UnidadDeTrabajo unidad) {
        this.stocks = stocks; this.promociones = promociones; this.unidad = unidad;
    }
    public PrecioCalculado valorizar(int productoId, int cantidad, String medioPago) {
        return unidad.consultar(() -> {
            Stock stock = stocks.cargarInventario().get(productoId);
            if (stock == null) throw new EntidadNoEncontradaException("Producto", productoId);
            return calcular(stock, cantidad, medioPago);
        });
    }
    @Override public List<ItemCatalogo> catalogo(int cantidad, String medioPago) {
        Reglas.positivo(cantidad, "cantidad");
        return unidad.consultar(() -> stocks.cargarInventario().values().stream().map(s ->
                new ItemCatalogo(s.getProducto().getId(), s.getProducto().getNombre(), s.getProducto().getDescripcion(),
                        s instanceof StockSimple ? "SIMPLE" : "COMBO", s.getDisponible(), calcular(s, cantidad, medioPago))).toList());
    }
    private PrecioCalculado calcular(Stock stock, int cantidad, String medioPago) {
            int productoId = stock.getProducto().getId();
            ItemValorizable precio = new PrecioLista(stock.getProducto(), cantidad);
            BigDecimal lista = moneda(precio.getPrecio());
            List<Long> aplicadas = new ArrayList<>();
            boolean dosPorUnoAplicado = false;
            for (Promocion p : promociones.listar(productoId)) {
                if (!p.activa() || (p.tipo() == Promocion.Tipo.MEDIO_PAGO && !p.medioPago().equalsIgnoreCase(medioPago))) continue;
                precio = switch (p.tipo()) {
                    case DESCUENTO -> new DescuentoDecorator(precio, p.porcentaje());
                    case DOS_POR_UNO -> {
                        if (dosPorUnoAplicado) throw new IllegalStateException("No se puede aplicar dos veces el 2x1");
                        dosPorUnoAplicado = true;
                        yield new PromocionDecorator(precio, PromocionDecorator.DOS_POR_UNO);
                    }
                    case MEDIO_PAGO -> new PromocionDecorator(precio, PromocionDecorator.MEDIO_DE_PAGO, p.porcentaje());
                };
                aplicadas.add(p.id());
            }
            BigDecimal total = moneda(precio.getPrecio());
            return new PrecioCalculado(productoId, cantidad, lista, total, lista.subtract(total), aplicadas);
    }
    private BigDecimal moneda(double importe) { return BigDecimal.valueOf(importe).setScale(2, RoundingMode.HALF_UP); }
}
