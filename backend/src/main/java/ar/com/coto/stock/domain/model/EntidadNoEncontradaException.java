package ar.com.coto.stock.domain.model;

/** No extiende IllegalArgumentException: Spring traduce esas excepciones cuando salen de un @Repository
 * y la API respondería 500 en lugar de 404. */
public final class EntidadNoEncontradaException extends RuntimeException {
    public EntidadNoEncontradaException(String entidad, long id) { super(entidad + " no encontrado: " + id); }
}
