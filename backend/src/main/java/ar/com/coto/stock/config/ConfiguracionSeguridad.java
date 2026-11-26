package ar.com.coto.stock.config;

import ar.com.coto.stock.adapter.UsuarioSecurityAdapter;
import ar.com.coto.stock.repository.RepositorioUsuariosJpa;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static ar.com.coto.stock.config.RolSucursal.*;

import java.util.Arrays;
import java.util.List;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errores) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        // ManejadorErrores arma el problem+json de 401 y 403 igual que el resto de los errores.
                        .authenticationEntryPoint((request, response, error) -> errores.resolveException(request, response, null, error))
                        .accessDeniedHandler((request, response, error) -> errores.resolveException(request, response, null, error))
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
                        .requestMatchers(HttpMethod.POST, "/api/temporadas/**").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.PUT, "/api/temporadas/**").hasAnyRole(roles(COMPRAS, SUPERVISOR))
                        .requestMatchers(HttpMethod.DELETE, "/api/temporadas/**").hasAnyRole(roles(COMPRAS, SUPERVISOR))

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

    private static String[] roles(RolSucursal... roles) {
        return Arrays.stream(roles).map(RolSucursal::name).toArray(String[]::new);
    }
}

