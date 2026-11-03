package ar.com.coto.stock.adapter;

import ar.com.coto.stock.repository.entity.UsuarioEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Patrón Adapter (GoF): Adapta el modelo relacional {@link UsuarioEntity}
 * al contrato {@link UserDetails} exigido por Spring Security, preservando la independencia
 * de nuestras entidades respecto a interfaces del framework.
 */
public class UsuarioSecurityAdapter implements UserDetails {
    private final UsuarioEntity usuario;

    public UsuarioSecurityAdapter(UsuarioEntity usuario) {
        this.usuario = usuario;
    }

    public UsuarioEntity getUsuario() {
        return usuario;
    }

    public String getRol() {
        return usuario.rol;
    }

    public String getNombreCompleto() {
        return usuario.nombreCompleto;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol));
    }

    @Override
    public String getPassword() {
        return usuario.passwordHash;
    }

    @Override
    public String getUsername() {
        return usuario.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return usuario.activo;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return usuario.activo;
    }
}

