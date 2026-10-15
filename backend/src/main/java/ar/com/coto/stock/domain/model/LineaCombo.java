package ar.com.coto.stock.domain.model;

import java.util.Objects;

public final class LineaCombo {
    private final int cantidadEnCombo;
    private final Stock componente;

    public LineaCombo(Stock componente, int cantidadEnCombo) {
        this.componente = Objects.requireNonNull(componente);
        this.cantidadEnCombo = Reglas.positivo(cantidadEnCombo, "cantidadEnCombo");
    }

    public int getCantidadEnCombo() { return cantidadEnCombo; }
    public Stock getComponente() { return componente; }
}
