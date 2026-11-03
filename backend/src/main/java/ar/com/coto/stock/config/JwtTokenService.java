package ar.com.coto.stock.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/** Servicio de generación y validación de tokens JWT sin estado. */
@Service
public class JwtTokenService {
    private final SecretKey key;
    private final long duracionHoras;

    public JwtTokenService(
            @Value("${coto.seguridad.jwt.secreto:coto-clave-secreta-de-seguridad-inventario-sucursal-super-segura-2026}") String secreto,
            @Value("${coto.seguridad.jwt.duracion-horas:12}") long duracionHoras) {
        this.key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracionHoras = duracionHoras;
    }

    public String generarToken(String username, String rol, String nombreCompleto) {
        Instant ahora = Instant.now();
        Instant expiracion = ahora.plus(duracionHoras, ChronoUnit.HOURS);

        return Jwts.builder()
                .subject(username)
                .claim("rol", rol)
                .claim("nombre", nombreCompleto)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiracion))
                .signWith(key)
                .compact();
    }

    public Optional<Claims> validarYObtenerClaims(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

