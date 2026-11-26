package ar.com.coto.stock.domain.valorizacion;

import java.math.BigDecimal;

/** getImporte retorna el TOTAL de la línea; getCantidad permite promociones por unidades. */
public interface ItemValorizable {
    BigDecimal getImporte();
    default int getCantidad() { return 1; }
}
