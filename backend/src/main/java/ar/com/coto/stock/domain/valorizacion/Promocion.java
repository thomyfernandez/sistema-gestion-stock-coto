package ar.com.coto.stock.domain.valorizacion;

import ar.com.coto.stock.domain.model.Reglas;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

/** ID cero identifica una promoción nueva. El orden define el anidamiento de decoradores. */
public record Promocion(long id, int productoId, Tipo tipo, BigDecimal porcentaje, int orden,
                        boolean activa, String medioPago) {
    public enum Tipo { DESCUENTO, DOS_POR_UNO, MEDIO_PAGO }
    public Promocion {
        if (id < 0) throw new IllegalArgumentException("ID inválido");
        Reglas.positivo(productoId, "productoId");
        Objects.requireNonNull(tipo);
        Reglas.porcentaje(porcentaje);
        Reglas.noNegativo(orden, "orden");
        if (tipo == Tipo.DOS_POR_UNO && porcentaje.signum() != 0) throw new IllegalArgumentException("2x1 no admite porcentaje");
        if (tipo == Tipo.MEDIO_PAGO) medioPago = Reglas.texto(medioPago, "medioPago");
        else if (medioPago != null) throw new IllegalArgumentException("Medio de pago sólo corresponde a MEDIO_PAGO");
    }

    /** Activa y, si es de medio de pago, del mismo medio elegido por el cliente. */
    public boolean aplicaA(String medioPagoCliente) {
        if (!activa) return false;
        return tipo != Tipo.MEDIO_PAGO || (medioPagoCliente != null && medioPago.equalsIgnoreCase(medioPagoCliente));
    }

    /** Un producto admite un solo 2x1 activo: dos 2x1 anidados regalarían más unidades de las previstas. */
    public static void exigirUnSoloDosPorUnoActivo(Collection<Promocion> promociones) {
        if (promociones.stream().filter(p -> p.activa() && p.tipo() == Tipo.DOS_POR_UNO).count() > 1) {
            throw new IllegalArgumentException("Ya existe una promoción 2x1 activa");
        }
    }
}
