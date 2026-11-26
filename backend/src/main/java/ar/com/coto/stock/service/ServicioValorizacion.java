package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.*;
import ar.com.coto.stock.domain.valorizacion.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ServicioValorizacion implements ConsultaPrecios {
    private final RepositorioStock stocks;
    private final RepositorioPromociones promociones;
    private final UnidadDeTrabajo unidad;
    public ServicioValorizacion(RepositorioStock stocks, RepositorioPromociones promociones, UnidadDeTrabajo unidad) {
        this.stocks = stocks; this.promociones = promociones; this.unidad = unidad;
    }
    public PrecioCalculado valorizar(int productoId, int cantidad, String medioPago) {
        return unidad.consultar(() -> {
            Producto producto = stocks.producto(productoId).orElseThrow(() -> new EntidadNoEncontradaException("Producto", productoId));
            return calcular(producto, promociones.listar(productoId), cantidad, medioPago);
        });
    }
    @Override public List<ItemCatalogo> catalogo(int cantidad, String medioPago) {
        Reglas.positivo(cantidad, "cantidad");
        return unidad.consultar(() -> {
            Map<Integer, List<Promocion>> porProducto = promociones.listarPorProducto();
            return stocks.cargarInventario().values().stream().map(s -> new ItemCatalogo(s.getProducto().getId(),
                    s.getProducto().getNombre(), s.getProducto().getDescripcion(), s.getTipo().name(), s.getDisponible(),
                    calcular(s.getProducto(), porProducto.getOrDefault(s.getProducto().getId(), List.of()), cantidad, medioPago))).toList();
        });
    }
    private static PrecioCalculado calcular(Producto producto, List<Promocion> promociones, int cantidad, String medioPago) {
        Promocion.exigirUnSoloDosPorUnoActivo(promociones);
        ItemValorizable precio = new PrecioLista(producto, cantidad);
        BigDecimal lista = moneda(precio.getImporte());
        List<Long> aplicadas = new ArrayList<>();
        for (Promocion p : promociones) {
            if (!p.aplicaA(medioPago)) continue;
            precio = switch (p.tipo()) {
                case DESCUENTO -> new DescuentoDecorator(precio, p.porcentaje());
                case DOS_POR_UNO -> new DosPorUnoDecorator(precio);
                case MEDIO_PAGO -> new MedioPagoDecorator(precio, p.porcentaje());
            };
            aplicadas.add(p.id());
        }
        BigDecimal total = moneda(precio.getImporte());
        return new PrecioCalculado(producto.getId(), cantidad, lista, total, lista.subtract(total), aplicadas);
    }

    private static BigDecimal moneda(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }
}
