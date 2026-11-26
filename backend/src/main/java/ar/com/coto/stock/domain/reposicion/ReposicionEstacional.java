package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Reglas;
import ar.com.coto.stock.domain.model.Stock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/** Estacionales: el objetivo base se multiplica por el factor de la temporada vigente en la fecha del reloj.
 * Fuera de toda temporada el factor es 1. La fecha sale siempre del {@link Clock} recibido. */
public final class ReposicionEstacional implements EstrategiaReposicion {
    /** Con un factor fijo la fecha no cambia el resultado; el reloj sólo cumple el contrato. */
    private static final Clock RELOJ_INDIFERENTE = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    private final int objetivoBase;
    private final CalendarioTemporadas calendario;
    private final Clock reloj;

    public ReposicionEstacional(int objetivoBase, CalendarioTemporadas calendario, Clock reloj) {
        this.objetivoBase = Reglas.positivo(objetivoBase, "objetivoBase");
        this.calendario = Objects.requireNonNull(calendario, "calendario");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
        // Un objetivo fuera de rango invalida la estrategia al construirla, no al reponer.
        objetivo(calendario.factorMaximo());
    }

    /** Factor fijo todo el año, sin calendario. */
    public ReposicionEstacional(int objetivoBase, double factorEstacional) {
        this(objetivoBase, calendarioFijo(factorEstacional), RELOJ_INDIFERENTE);
    }

    private static CalendarioTemporadas calendarioFijo(double factor) {
        Reglas.noNegativo(factor, "factorEstacional");
        if (factor == 0) throw new IllegalArgumentException("El factor debe ser mayor a cero");
        return CalendarioTemporadas.fijo(BigDecimal.valueOf(factor));
    }

    private int objetivo(BigDecimal factor) {
        return BigDecimal.valueOf(objetivoBase).multiply(factor).setScale(0, RoundingMode.CEILING).intValueExact();
    }

    /** Factor que rige hoy según el reloj. */
    public CalendarioTemporadas.FactorVigente factorVigente() {
        return calendario.factorPara(LocalDate.now(reloj));
    }

    @Override public int calcularCantidad(Stock s) {
        return Math.max(0, objetivo(factorVigente().factor()) - Objects.requireNonNull(s).getDisponible());
    }
}
