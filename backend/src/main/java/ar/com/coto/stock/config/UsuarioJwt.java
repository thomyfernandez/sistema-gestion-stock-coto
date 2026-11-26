package ar.com.coto.stock.config;

import org.springframework.security.core.AuthenticatedPrincipal;

/** Principal liviano construido desde los claims validados del JWT. */
public record UsuarioJwt(String username, String nombreCompleto) implements AuthenticatedPrincipal {
    @Override
    public String getName() {
        return username;
    }
}
