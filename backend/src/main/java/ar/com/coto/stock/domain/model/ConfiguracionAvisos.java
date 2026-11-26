package ar.com.coto.stock.domain.model;

/** Destinatarios generales de la sucursal. */
public record ConfiguracionAvisos(Canal canal, String email, String telefono) {
    public ConfiguracionAvisos {
        if (canal == null) throw new IllegalArgumentException("Canal desconocido");
        email = Reglas.texto(email, "email");
        telefono = telefono == null ? "" : telefono.trim();
        if (!Reglas.esEmail(email)) throw new IllegalArgumentException("Indicá un email válido");
        if (!telefono.isEmpty() && !Reglas.esTelefono(telefono)) throw new IllegalArgumentException("Indicá un teléfono válido");
        if (!canal.usaEmail() && telefono.isEmpty()) throw new IllegalArgumentException("El canal seleccionado requiere un teléfono");
    }
    public String destino() { return canal.usaEmail() ? email : telefono; }
}
