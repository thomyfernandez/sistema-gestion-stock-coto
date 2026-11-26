package ar.com.coto.stock.domain.reposicion;

import ar.com.coto.stock.domain.model.Reglas;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Objects;

/** Época del año en la que uno o varios artículos, o toda una categoría, se reponen con un objetivo multiplicado.
 * Las fechas son día y mes, sin año: la temporada se repite todos los años. Si {@code desde} es posterior
 * a {@code hasta}, la temporada cruza el fin de año (por ejemplo, del 15/11 al 06/01).
 * Alcance: una lista no vacía de artículos simples ({@code stockIds}) o una categoría, nunca ambos. */
public record Temporada(long id, String nombre, List<Integer> stockIds, Integer categoriaId,
                        MonthDay desde, MonthDay hasta, BigDecimal factor) {
    /** Tope del factor: con él, un objetivo base admitido por la política nunca desborda. */
    public static final BigDecimal FACTOR_MAXIMO = BigDecimal.TEN;
    public static final int LARGO_NOMBRE = 80;
    public static final int MAXIMO_ARTICULOS = 200;

    public Temporada {
        if (id < 0) throw new IllegalArgumentException("id no puede ser negativo");
        nombre = Reglas.texto(nombre, "nombre");
        if (nombre.length() > LARGO_NOMBRE) throw new IllegalArgumentException("El nombre admite hasta " + LARGO_NOMBRE + " caracteres");
        // Sin duplicados y en orden: dos listas con los mismos artículos son la misma temporada.
        stockIds = stockIds == null ? List.of() : stockIds.stream().map(Objects::requireNonNull).distinct().sorted().toList();
        if (stockIds.isEmpty() == (categoriaId == null)) {
            throw new IllegalArgumentException("Indicá uno o más artículos o una categoría para la temporada, no ambos");
        }
        if (stockIds.size() > MAXIMO_ARTICULOS) throw new IllegalArgumentException("Una temporada admite hasta " + MAXIMO_ARTICULOS + " artículos");
        stockIds.forEach(s -> Reglas.positivo(s, "stockId"));
        if (categoriaId != null) Reglas.positivo(categoriaId, "categoriaId");
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
        Objects.requireNonNull(factor, "factor");
        if (factor.signum() <= 0) throw new IllegalArgumentException("El factor de temporada debe ser mayor a cero");
        if (factor.compareTo(FACTOR_MAXIMO) > 0) throw new IllegalArgumentException("El factor de temporada no puede superar " + FACTOR_MAXIMO);
        if (factor.stripTrailingZeros().scale() > 4) throw new IllegalArgumentException("El factor de temporada admite hasta 4 decimales");
    }

    /** Temporada de un único artículo. */
    public static Temporada deArticulo(long id, String nombre, int stockId, MonthDay desde, MonthDay hasta, BigDecimal factor) {
        return new Temporada(id, nombre, List.of(stockId), null, desde, hasta, factor);
    }
    /** Temporada de toda una categoría. */
    public static Temporada deCategoria(long id, String nombre, int categoriaId, MonthDay desde, MonthDay hasta, BigDecimal factor) {
        return new Temporada(id, nombre, List.of(), categoriaId, desde, hasta, factor);
    }

    public boolean esDeArticulos() { return !stockIds.isEmpty(); }
    public boolean incluyeArticulo(int stockId) { return stockIds.contains(stockId); }
    public boolean cruzaElAnio() { return desde.isAfter(hasta); }

    public boolean incluye(LocalDate fecha) {
        MonthDay dia = MonthDay.from(Objects.requireNonNull(fecha));
        return cruzaElAnio()
                ? !dia.isBefore(desde) || !dia.isAfter(hasta)
                : !dia.isBefore(desde) && !dia.isAfter(hasta);
    }

    /** Artículos que esta temporada comparte con otra; vacío si alguna es de categoría. */
    public List<Integer> articulosEnComun(Temporada otra) {
        return stockIds.stream().filter(otra.stockIds::contains).toList();
    }

    /** Comparten alcance si son de la misma categoría o tienen al menos un artículo en común. */
    public boolean comparteAlcance(Temporada otra) {
        return categoriaId != null ? categoriaId.equals(otra.categoriaId) : !articulosEnComun(otra).isEmpty();
    }

    /** Dos temporadas se superponen si comparten al menos un día de cualquier año, bisiestos incluidos. */
    public boolean superponeCon(Temporada otra) {
        // 2024 es bisiesto: recorrerlo cubre todos los días posibles, 29/02 incluido.
        for (LocalDate dia = LocalDate.of(2024, 1, 1); dia.getYear() == 2024; dia = dia.plusDays(1)) {
            if (incluye(dia) && otra.incluye(dia)) return true;
        }
        return false;
    }

    public Temporada conId(long nuevoId) {
        return new Temporada(nuevoId, nombre, stockIds, categoriaId, desde, hasta, factor);
    }
}
