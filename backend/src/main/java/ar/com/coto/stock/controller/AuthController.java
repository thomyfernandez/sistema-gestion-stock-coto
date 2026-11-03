package ar.com.coto.stock.controller;

import ar.com.coto.stock.adapter.UsuarioSecurityAdapter;
import ar.com.coto.stock.config.JwtTokenService;
import ar.com.coto.stock.dto.Respuestas;
import ar.com.coto.stock.dto.Solicitudes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Inicio de sesión y consulta de perfil de usuario.")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    @Operation(summary = "Inicio de sesión", description = "Valida credenciales y genera un token JWT sin estado.")
    public ResponseEntity<Respuestas.Sesion> login(@Valid @RequestBody Solicitudes.Login solicitud) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(solicitud.username().trim(), solicitud.password())
            );

            if (auth.getPrincipal() instanceof UsuarioSecurityAdapter userDetails) {
                String token = jwtTokenService.generarToken(
                        userDetails.getUsername(),
                        userDetails.getRol(),
                        userDetails.getNombreCompleto()
                );
                return ResponseEntity.ok(new Respuestas.Sesion(
                        token,
                        userDetails.getUsername(),
                        userDetails.getNombreCompleto(),
                        userDetails.getRol()
                ));
            }

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        } catch (BadCredentialsException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
        }
    }

    @GetMapping("/perfil")
    @Operation(summary = "Perfil de usuario", description = "Retorna los datos del usuario autenticado actual.")
    public ResponseEntity<Respuestas.Perfil> perfil(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        String rol = auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("DESCONOCIDO");

        return ResponseEntity.ok(new Respuestas.Perfil(auth.getName(), auth.getName(), rol));
    }
}

