package ar.com.coto.stock.domain.valorizacion;

/** getPrecio retorna el TOTAL de la línea; getCantidad permite promociones por unidades. */
public interface ItemValorizable {
    double getPrecio();
    default int getCantidad() { return 1; }
}
