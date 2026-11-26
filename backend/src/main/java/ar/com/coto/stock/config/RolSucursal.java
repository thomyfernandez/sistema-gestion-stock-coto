package ar.com.coto.stock.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Roles de sucursal y su traducción a autoridades de Spring Security. */
public enum RolSucursal {
    RECEPCION, REPOSITOR, COMPRAS, SUPERVISOR;

    private static final String PREFIJO_AUTORIDAD = "ROLE_";

    public static GrantedAuthority autoridad(String rol) {
        return new SimpleGrantedAuthority(PREFIJO_AUTORIDAD + rol);
    }

    public static String rol(GrantedAuthority autoridad) {
        String valor = autoridad.getAuthority();
        return valor.startsWith(PREFIJO_AUTORIDAD) ? valor.substring(PREFIJO_AUTORIDAD.length()) : valor;
    }
}
