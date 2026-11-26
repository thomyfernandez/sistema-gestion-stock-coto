package ar.com.coto.stock.domain.model;

public final class StockInsuficienteException extends EstadoInvalidoException {
    public StockInsuficienteException(String mensaje) { super(mensaje); }
}
