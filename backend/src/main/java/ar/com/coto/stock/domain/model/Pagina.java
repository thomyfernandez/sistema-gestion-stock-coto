package ar.com.coto.stock.domain.model;

import java.util.List;

/** Porción de un listado ordenado y el total de elementos que cumplen el filtro. */
public record Pagina<T>(List<T> elementos, long total) {
    public Pagina {
        elementos = List.copyOf(elementos);
        if (total < elementos.size()) throw new IllegalArgumentException("Total de página inválido");
    }
}
