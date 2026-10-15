package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Reglas;
import ar.com.coto.stock.domain.model.Stock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Estacionales: ajusta el objetivo por un factor configurable de temporada. */
public final class ReposicionEstacional implements EstrategiaReposicion {
    private final int objetivoBase;
    private final double factorEstacional;

    public ReposicionEstacional(int objetivoBase, double factorEstacional) {
        this.objetivoBase = Reglas.positivo(objetivoBase, "objetivoBase");
        Reglas.noNegativo(factorEstacional, "factorEstacional");
        if (factorEstacional == 0) throw new IllegalArgumentException("El factor debe ser mayor a cero");
        this.factorEstacional = factorEstacional;
        objetivo();
    }

    private int objetivo() {
        return BigDecimal.valueOf(objetivoBase).multiply(BigDecimal.valueOf(factorEstacional))
                .setScale(0, RoundingMode.CEILING).intValueExact();
    }

    @Override public int calcularCantidad(Stock s) {
        return Math.max(0, objetivo() - Objects.requireNonNull(s).getDisponible());
    }
}
