package ar.com.coto.stock.domain.model;

/** Destinatario interno de avisos: un sector, equipo o responsable de la sucursal. */
public record Contacto(long id, String nombre, String email, String telefono) {
    public Contacto {
        if (id < 0) throw new IllegalArgumentException("ID de contacto inválido");
        nombre = Reglas.texto(nombre, "nombre");
        email = email == null ? "" : email.trim();
        telefono = telefono == null ? "" : telefono.trim();
        if (nombre.length() > 160) throw new IllegalArgumentException("El nombre admite hasta 160 caracteres");
        if (email.isEmpty() && telefono.isEmpty()) throw new IllegalArgumentException("Cargá un email o un teléfono de contacto");
        if (email.length() > 200 || (!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) throw new IllegalArgumentException("Indicá un email válido");
        if (telefono.length() > 30 || (!telefono.isEmpty() && !telefono.matches("\\+?[0-9][0-9 ()-]{5,29}"))) throw new IllegalArgumentException("Indicá un teléfono válido");
    }
    public String destino(String canal) {
        if (canal == null || !java.util.Set.of("EMAIL", "SMS", "WHATSAPP").contains(canal)) throw new IllegalArgumentException("Canal desconocido");
        String destino = canal.equals("EMAIL") ? email : telefono;
        if (destino.isEmpty()) throw new IllegalArgumentException(canal.equals("EMAIL") ? "El contacto no tiene email de contacto" : "El contacto no tiene teléfono de contacto");
        return destino;
    }
}
