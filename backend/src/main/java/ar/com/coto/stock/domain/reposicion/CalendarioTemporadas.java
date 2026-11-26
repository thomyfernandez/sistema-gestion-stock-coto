package ar.com.coto.stock.domain.reposicion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Temporadas que aplican a un artículo: las que lo incluyen y las de su categoría.
 * Resolución jerárquica, igual que los avisos: artículo → categoría → sin temporada (factor 1). */
public final class CalendarioTemporadas {
    /** FIJO: estrategia armada con un factor constante, sin temporadas cargadas. */
    public enum Origen { ARTICULO, CATEGORIA, SIN_TEMPORADA, FIJO }
    /** Factor que rige en una fecha; {@code temporada} es nula fuera de toda temporada. */
    public record FactorVigente(BigDecimal factor, Temporada temporada, Origen origen) {
        public FactorVigente { Objects.requireNonNull(factor); Objects.requireNonNull(origen); }
    }
    private static final FactorVigente SIN_TEMPORADA = new FactorVigente(BigDecimal.ONE, null, Origen.SIN_TEMPORADA);
    private static final CalendarioTemporadas VACIO = new CalendarioTemporadas(List.of(), List.of(), null);

    private final List<Temporada> delArticulo;
    private final List<Temporada> deCategoria;
    private final BigDecimal fijo;

    private CalendarioTemporadas(List<Temporada> delArticulo, List<Temporada> deCategoria, BigDecimal fijo) {
        this.delArticulo = List.copyOf(delArticulo);
        this.deCategoria = List.copyOf(deCategoria);
        this.fijo = fijo;
    }

    public static CalendarioTemporadas vacio() { return VACIO; }

    /** El mismo factor cualquier día del año. */
    public static CalendarioTemporadas fijo(BigDecimal factor) {
        if (Objects.requireNonNull(factor).signum() <= 0) throw new IllegalArgumentException("El factor debe ser mayor a cero");
        return new CalendarioTemporadas(List.of(), List.of(), factor);
    }

    /** Filtra, entre todas las temporadas, las del artículo y las de su categoría (puede no tener). */
    public static CalendarioTemporadas para(int stockId, Integer categoriaId, Collection<Temporada> temporadas) {
        List<Temporada> todas = List.copyOf(temporadas);
        return new CalendarioTemporadas(
                todas.stream().filter(t -> t.incluyeArticulo(stockId)).toList(),
                categoriaId == null ? List.of() : todas.stream().filter(t -> categoriaId.equals(t.categoriaId())).toList(), null);
    }

    public FactorVigente factorPara(LocalDate fecha) {
        Objects.requireNonNull(fecha);
        if (fijo != null) return new FactorVigente(fijo, null, Origen.FIJO);
        Optional<Temporada> propia = delArticulo.stream().filter(t -> t.incluye(fecha)).findFirst();
        if (propia.isPresent()) return new FactorVigente(propia.get().factor(), propia.get(), Origen.ARTICULO);
        return deCategoria.stream().filter(t -> t.incluye(fecha)).findFirst()
                .map(t -> new FactorVigente(t.factor(), t, Origen.CATEGORIA))
                .orElse(SIN_TEMPORADA);
    }

    /** El mayor factor que puede regir en algún momento del año; nunca menor a 1. */
    public BigDecimal factorMaximo() {
        if (fijo != null) return fijo;
        return java.util.stream.Stream.concat(delArticulo.stream(), deCategoria.stream())
                .map(Temporada::factor).reduce(BigDecimal.ONE, BigDecimal::max);
    }
}
