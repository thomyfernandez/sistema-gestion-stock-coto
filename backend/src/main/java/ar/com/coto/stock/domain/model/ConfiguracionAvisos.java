package ar.com.coto.stock.domain.model;

/** Destinatarios generales de la sucursal. */
public record ConfiguracionAvisos(String canal, String email, String telefono) {
    public ConfiguracionAvisos {
        if (!java.util.Set.of("EMAIL", "SMS", "WHATSAPP").contains(canal)) throw new IllegalArgumentException("Canal desconocido");
        email = Reglas.texto(email, "email");
        telefono = telefono == null ? "" : telefono.trim();
        if (email.length() > 200 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Indicá un email válido");
        if (telefono.length() > 30 || (!telefono.isEmpty() && !telefono.matches("\\+?[0-9][0-9 ()-]{5,29}"))) throw new IllegalArgumentException("Indicá un teléfono válido");
        if (!canal.equals("EMAIL") && telefono.isEmpty()) throw new IllegalArgumentException("El canal seleccionado requiere un teléfono");
    }
    public String destino() { return canal.equals("EMAIL") ? email : telefono; }
}
