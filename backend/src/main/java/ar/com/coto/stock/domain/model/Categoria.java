package ar.com.coto.stock.domain.model;

/** Clasificación comercial independiente del tipo SIMPLE/COMBO. */
public record Categoria(int id, String nombre, Long contactoId, String canalAlerta) {
    public Categoria {
        if (id < 0) throw new IllegalArgumentException("ID de categoría inválido");
        nombre = Reglas.texto(nombre, "nombre");
        if (nombre.length() > 80) throw new IllegalArgumentException("La categoría admite hasta 80 caracteres");
        if (contactoId == null && canalAlerta != null) throw new IllegalArgumentException("Elegí un contacto para el canal de la categoría");
        if (contactoId != null && (contactoId <= 0 || canalAlerta == null || !java.util.Set.of("EMAIL", "SMS", "WHATSAPP").contains(canalAlerta)))
            throw new IllegalArgumentException("Elegí un contacto y canal válidos para la categoría");
    }
}
