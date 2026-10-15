package ar.com.coto.stock.domain.model;

public final class StockInsuficienteException extends IllegalStateException {
    public StockInsuficienteException(String mensaje) { super(mensaje); }
}
