package ar.com.coto.stock.domain.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Lista de emails que recibe los avisos de stock bajo. Sin categoría, es la lista general de la sucursal. */
public record ListaDestinatarios(Integer categoriaId, List<String> emails) {
    public static final int MAXIMO = 20;

    public ListaDestinatarios {
        if (categoriaId != null && categoriaId <= 0) throw new IllegalArgumentException("ID de categoría inválido");
        Objects.requireNonNull(emails, "emails");
        var normalizados = new LinkedHashSet<String>();
        for (String email : emails) {
            String valor = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
            if (valor.isEmpty()) continue;
            if (valor.length() > 200 || !valor.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                throw new IllegalArgumentException("Indicá un email válido: " + email.trim());
            normalizados.add(valor);
        }
        if (normalizados.size() > MAXIMO) throw new IllegalArgumentException("Cada lista admite hasta " + MAXIMO + " emails");
        emails = List.copyOf(normalizados);
    }

    public static ListaDestinatarios general(List<String> emails) { return new ListaDestinatarios(null, emails); }
    public boolean esGeneral() { return categoriaId == null; }
    public boolean vacia() { return emails.isEmpty(); }
}
