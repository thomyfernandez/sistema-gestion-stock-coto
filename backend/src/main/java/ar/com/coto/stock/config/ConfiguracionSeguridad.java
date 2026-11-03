package ar.com.coto.stock.config;

import ar.com.coto.stock.adapter.UsuarioSecurityAdapter;
import ar.com.coto.stock.repository.RepositorioUsuariosJpa;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
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
        // objectMapper instanciado
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
        config.setMaxAge(3600L);

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
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            Map<String, Object> body = new LinkedHashMap<>();
                            body.put("type", "about:blank");
                            body.put("title", "No autenticado");
                            body.put("status", 401);
                            body.put("detail", "Se requiere token Bearer válido para acceder.");
                            body.put("instance", request.getRequestURI());
                            body.put("codigo", "NO_AUTENTICADO");
                            body.put("timestamp", Instant.now().toString());
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            Map<String, Object> body = new LinkedHashMap<>();
                            body.put("type", "about:blank");
                            body.put("title", "Acceso denegado");
                            body.put("status", 403);
                            body.put("detail", "El rol del usuario no tiene permisos para esta acción.");
                            body.put("instance", request.getRequestURI());
                            body.put("codigo", "ACCESO_DENEGADO");
                            body.put("timestamp", Instant.now().toString());
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        })
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
                        .requestMatchers(HttpMethod.POST, "/api/recepcion/ingresos").hasAnyRole("RECEPCION", "SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/recepcion").hasAnyRole("RECEPCION", "SUPERVISOR")

                        // 2. Reposición / Sucursal física
                        .requestMatchers(HttpMethod.POST, "/api/sucursal/movimientos").hasAnyRole("REPOSITOR", "SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/combos/*/armado", "/api/combos/*/desarmado").hasAnyRole("REPOSITOR", "SUPERVISOR")

                        // 3. Compras
                        .requestMatchers(HttpMethod.POST, "/api/productos/**").hasAnyRole("COMPRAS", "SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/combos/**").hasAnyRole("COMPRAS", "SUPERVISOR")
                        .requestMatchers(HttpMethod.PUT, "/api/productos/*/politica-reposicion").hasAnyRole("COMPRAS", "SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/reposicion/**").hasAnyRole("COMPRAS", "SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/cancelacion").hasAnyRole("COMPRAS", "SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/productos/*/promociones/**").hasAnyRole("COMPRAS", "SUPERVISOR")
                        .requestMatchers(HttpMethod.PUT, "/api/productos/*/promociones/**").hasAnyRole("COMPRAS", "SUPERVISOR")

                        // 4. Canal Digital & Configuración exclusiva de Supervisor
                        .requestMatchers(HttpMethod.POST, "/api/digital/**").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/configuracion/**").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.PUT, "/api/configuracion/**").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/configuracion/**").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/categorias/**").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.PUT, "/api/categorias/**").hasRole("SUPERVISOR")

                        // Todas las demás consultas /api/** requieren estar autenticado (cualquier rol válido)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}

