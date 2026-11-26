package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Reglas;
import ar.com.coto.stock.domain.model.Stock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Frescos: cubre ceil(demandaDiaria * diasCobertura) unidades, sin sobrecomprar. */
public final class ReposicionPorDemanda implements EstrategiaReposicion {
    /** Se calcula al construir: un objetivo fuera de rango invalida la política antes de guardarla. */
    private final int objetivo;

    public ReposicionPorDemanda(double demandaDiaria, int diasCobertura) {
        Reglas.noNegativo(demandaDiaria, "demandaDiaria");
        Reglas.positivo(diasCobertura, "diasCobertura");
        this.objetivo = BigDecimal.valueOf(demandaDiaria).multiply(BigDecimal.valueOf(diasCobertura))
                .setScale(0, RoundingMode.CEILING).intValueExact();
    }

    @Override public int calcularCantidad(Stock s) {
        return Math.max(0, objetivo - Objects.requireNonNull(s).getDisponible());
    }
}
