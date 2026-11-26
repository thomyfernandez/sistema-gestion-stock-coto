package ar.com.coto.stock.config;

import ar.com.coto.stock.adapter.UsuarioSecurityAdapter;
import ar.com.coto.stock.repository.RepositorioUsuariosJpa;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static ar.com.coto.stock.config.RolSucursal.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración de seguridad Spring Security.
 * Implementa el patrón Strategy mediante la interfaz PasswordEncoder.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ConfiguracionSeguridad {
    private static final long CORS_MAX_AGE_SEGUNDOS = 3600L;

    private final FiltroAutenticacionJwt filtroJwt;
    private final RepositorioUsuariosJpa repositorioUsuarios;
    private final List<String> origenes;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConfiguracionSeguridad(
            FiltroAutenticacionJwt filtroJwt,
            RepositorioUsuariosJpa repositorioUsuarios,
            @Value("${coto.web.origenes}") List<String> origenes) {
        this.filtroJwt = filtroJwt;
        this.repositorioUsuarios = repositorioUsuarios;
        this.origenes = List.copyOf(origenes);
    }

    /**
     * Patrón Strategy: BCryptPasswordEncoder como implementación concreta de hashing.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Carga el usuario adaptándolo al contrato UserDetails de Spring Security.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> repositorioUsuarios.buscarPorUsername(username)
                .map(UsuarioSecurityAdapter::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "Accept"));
        config.setExposedHeaders(List.of("X-Total-Count", "Location"));
        config.setMaxAge(CORS_MAX_AGE_SEGUNDOS);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> escribirError(
                                response, request, HttpServletResponse.SC_UNAUTHORIZED,
                                "No autenticado", "Se requiere token Bearer válido para acceder.", "NO_AUTENTICADO"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> escribirError(
                                response, request, HttpServletResponse.SC_FORBIDDEN,
                                "Acceso denegado", "El rol del usuario no tiene permisos para esta acción.", "ACCESO_DENEGADO"))
                )
                .authorizeHttpRequests(auth -> auth
                        // Rutas públicas de auth, documentación Swagger y estáticos
                        .requestMatchers(
                                "/",
                                "/error",
                                "/api/estado",
                                "/api/auth/login",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/design-system.html",
                                "/favicon.ico"
                        ).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Matriz de roles de sucursal
                        // 1. Recepción
                        .requestMatchers(HttpMethod.POST, "/api/recepcion/ingresos").hasAnyRole(roles(RECEPCION, SUPERVISOR))
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/recepcion").hasAnyRole(roles(RECEPCION, SUPERVISOR))

                        // 2. Reposición / Sucursal física
                        .requestMatchers(HttpMethod.POST, "/api/sucursal/movimientos").hasAnyRole(roles(REPOSITOR, SUPERVISOR))
                        .requestMatchers(HttpMethod.POST, "/api/combos/*/armado", "/api/combos/*/desarmado").hasAnyRole(roles(REPOSITOR, SUPERVISOR))

                        // 3. Compras
                        .requestMatchers(HttpMethod.POST, "/api/productos/**").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.PUT, "/api/productos/*/categoria").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.POST, "/api/combos/**").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.PUT, "/api/productos/*/politica-reposicion").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.POST, "/api/reposicion/**").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/cancelacion").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.POST, "/api/productos/*/promociones/**").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.PUT, "/api/productos/*/promociones/**").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.DELETE, "/api/productos/*/promociones/**").hasAnyRole(roles(COMPRAS, SUPERVISOR))

                        // 4. Canal Digital & Configuración exclusiva de Supervisor
                        .requestMatchers(HttpMethod.POST, "/api/movimientos").hasRole(SUPERVISOR.name())
                        .requestMatchers(HttpMethod.POST, "/api/digital/**").hasRole(SUPERVISOR.name())
                        .requestMatchers(HttpMethod.POST, "/api/configuracion/**").hasRole(SUPERVISOR.name())
                        .requestMatchers(HttpMethod.PUT, "/api/configuracion/**").hasRole(SUPERVISOR.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/configuracion/**").hasRole(SUPERVISOR.name())
                        .requestMatchers(HttpMethod.PUT, "/api/notificaciones/canal").hasRole(SUPERVISOR.name())
                        .requestMatchers(HttpMethod.POST, "/api/integraciones/reintentos").hasRole(SUPERVISOR.name())
                        .requestMatchers(HttpMethod.POST, "/api/categorias/**").hasRole(SUPERVISOR.name())
                        .requestMatchers(HttpMethod.PUT, "/api/categorias/**").hasRole(SUPERVISOR.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/demo/proveedores/**").hasRole(SUPERVISOR.name())

                        // Todas las demás consultas /api/** requieren estar autenticado (cualquier rol válido)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    private void escribirError(HttpServletResponse response, HttpServletRequest request,
                               int status, String title, String detail, String codigo) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("codigo", codigo);
        body.put("timestamp", Instant.now().toString());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static String[] roles(RolSucursal... roles) {
        return Arrays.stream(roles).map(RolSucursal::name).toArray(String[]::new);
    }
}

