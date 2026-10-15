package ar.com.coto.stock.domain.valorizacion;

import ar.com.coto.stock.domain.model.Reglas;
import java.util.Objects;

/** ID cero identifica una promoción nueva. El orden define el anidamiento de decoradores. */
public record Promocion(long id, int productoId, Tipo tipo, double porcentaje, int orden,
                        boolean activa, String medioPago) {
    public enum Tipo { DESCUENTO, DOS_POR_UNO, MEDIO_PAGO }
    public Promocion {
        if (id < 0) throw new IllegalArgumentException("ID inválido");
        Reglas.positivo(productoId, "productoId");
        Objects.requireNonNull(tipo);
        Reglas.porcentaje(porcentaje);
        Reglas.noNegativo(orden, "orden");
        if (tipo == Tipo.DOS_POR_UNO && porcentaje != 0) throw new IllegalArgumentException("2x1 no admite porcentaje");
        if (tipo == Tipo.MEDIO_PAGO) medioPago = Reglas.texto(medioPago, "medioPago");
        else if (medioPago != null) throw new IllegalArgumentException("Medio de pago sólo corresponde a MEDIO_PAGO");
    }
}
