package ar.com.coto.stock.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de inspección JWT en cada request HTTP. Extrae y valida la cabecera Authorization Bearer.
 */
@Component
public class FiltroAutenticacionJwt extends OncePerRequestFilter {
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public FiltroAutenticacionJwt(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HEADER_AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(PREFIJO_BEARER)) {
            String token = authHeader.substring(PREFIJO_BEARER.length()).trim();
            jwtTokenService.validarYObtenerClaims(token).ifPresent(claims -> {
                String username = claims.getSubject();
                String rol = claims.get("rol", String.class);
                String nombre = claims.get("nombre", String.class);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    var authorities = rol != null
                            ? List.of(RolSucursal.autoridad(rol))
                            : List.<GrantedAuthority>of();

                    var principal = new UsuarioJwt(username, nombre != null && !nombre.isBlank() ? nombre : username);
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            });
        }

        filterChain.doFilter(request, response);
    }
}

