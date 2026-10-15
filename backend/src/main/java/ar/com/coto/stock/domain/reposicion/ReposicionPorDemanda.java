package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Reglas;
import ar.com.coto.stock.domain.model.Stock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Frescos: cubre ceil(demandaDiaria * diasCobertura) unidades, sin sobrecomprar. */
public final class ReposicionPorDemanda implements EstrategiaReposicion {
    private final double demandaDiaria;
    private final int diasCobertura;

    public ReposicionPorDemanda(double demandaDiaria, int diasCobertura) {
        this.demandaDiaria = Reglas.noNegativo(demandaDiaria, "demandaDiaria");
        this.diasCobertura = Reglas.positivo(diasCobertura, "diasCobertura");
        objetivo();
    }

    private int objetivo() {
        return BigDecimal.valueOf(demandaDiaria).multiply(BigDecimal.valueOf(diasCobertura))
                .setScale(0, RoundingMode.CEILING).intValueExact();
    }

    @Override public int calcularCantidad(Stock s) {
        return Math.max(0, objetivo() - Objects.requireNonNull(s).getDisponible());
    }
}
