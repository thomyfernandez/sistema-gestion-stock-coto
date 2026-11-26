package ar.com.coto.stock.domain.model;

import java.util.Objects;

/** Destinatario interno de avisos: un sector, equipo o responsable de la sucursal. */
public record Contacto(long id, String nombre, String email, String telefono) {
    public Contacto {
        if (id < 0) throw new IllegalArgumentException("ID de contacto inválido");
        nombre = Reglas.texto(nombre, "nombre");
        email = email == null ? "" : email.trim();
        telefono = telefono == null ? "" : telefono.trim();
        if (nombre.length() > 160) throw new IllegalArgumentException("El nombre admite hasta 160 caracteres");
        if (email.isEmpty() && telefono.isEmpty()) throw new IllegalArgumentException("Cargá un email o un teléfono de contacto");
        if (!email.isEmpty() && !Reglas.esEmail(email)) throw new IllegalArgumentException("Indicá un email válido");
        if (!telefono.isEmpty() && !Reglas.esTelefono(telefono)) throw new IllegalArgumentException("Indicá un teléfono válido");
    }
    /** Indica si el contacto tiene el dato que necesita el canal. */
    public boolean admite(Canal canal) {
        return !(Objects.requireNonNull(canal, "canal").usaEmail() ? email : telefono).isEmpty();
    }
    public String destino(Canal canal) {
        if (!admite(canal)) throw new IllegalArgumentException(canal.usaEmail() ? "El contacto no tiene email de contacto" : "El contacto no tiene teléfono de contacto");
        return canal.usaEmail() ? email : telefono;
    }
}
