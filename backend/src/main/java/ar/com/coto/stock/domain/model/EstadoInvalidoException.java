package ar.com.coto.stock.domain.model;

/** La operación no es compatible con el estado actual (pedido, combo, promociones). La API responde 409.
 * No extiende IllegalStateException: los errores internos no deben llegar al cliente como reglas de negocio. */
public class EstadoInvalidoException extends RuntimeException {
    public EstadoInvalidoException(String mensaje) { super(mensaje); }
}
