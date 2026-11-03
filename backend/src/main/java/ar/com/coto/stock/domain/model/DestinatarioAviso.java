package ar.com.coto.stock.domain.model;

import java.util.regex.Pattern;

/**
 * Destinatario de avisos de stock bajo por correo electrónico.
 * Cuando categoriaId es null, el destinatario pertenece a la lista general de la sucursal.
 */
public record DestinatarioAviso(Long id, Integer categoriaId, String email) {
    private static final Pattern PATRON_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public DestinatarioAviso(Integer categoriaId, String email) {
        this(null, categoriaId, email);
    }

    public DestinatarioAviso {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("El id debe ser positivo");
        }
        if (categoriaId != null && categoriaId <= 0) {
            throw new IllegalArgumentException("El categoriaId debe ser positivo");
        }
        email = Reglas.texto(email, "email").toLowerCase();
        if (!PATRON_EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("El formato del correo electrónico no es válido");
        }
    }

    public boolean esGeneral() {
        return categoriaId == null;
    }
}
