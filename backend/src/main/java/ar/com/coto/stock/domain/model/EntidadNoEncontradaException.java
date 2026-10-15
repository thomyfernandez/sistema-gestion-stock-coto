package ar.com.coto.stock.domain.model;

public final class EntidadNoEncontradaException extends IllegalArgumentException {
    public EntidadNoEncontradaException(String entidad, long id) { super(entidad + " no encontrado: " + id); }
}
