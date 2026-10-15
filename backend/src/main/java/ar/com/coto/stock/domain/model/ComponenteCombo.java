package ar.com.coto.stock.domain.model;

public record ComponenteCombo(int stockId, int cantidad) {
    public ComponenteCombo { Reglas.positivo(stockId, "stockId"); Reglas.positivo(cantidad, "cantidad"); }
}
