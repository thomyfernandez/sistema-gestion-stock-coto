package ar.com.coto.stock.domain.reposicion;

/** Dos temporadas que comparten un artículo, o de la misma categoría, no pueden compartir días: el factor
 * vigente tiene que ser único. La API responde 409. */
public final class TemporadaSuperpuestaException extends RuntimeException {
    private final Temporada existente;
    /** @param donde dónde chocan, ya redactado: "en Pan dulce COTO 500 g" o "en la misma categoría". */
    public TemporadaSuperpuestaException(Temporada existente, String donde) {
        super("Se superpone con «%s» (%02d/%02d al %02d/%02d) %s. Cambiá las fechas o editá esa temporada.".formatted(
                existente.nombre(), existente.desde().getDayOfMonth(), existente.desde().getMonthValue(),
                existente.hasta().getDayOfMonth(), existente.hasta().getMonthValue(), donde));
        this.existente = existente;
    }
    public Temporada getExistente() { return existente; }
}
