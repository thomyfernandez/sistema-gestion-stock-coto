package ar.com.coto.stock.config;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    @Test
    void rechazaSecretoNuloOVacio() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService(null, 12));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService("   ", 12));
    }

    @Test
    void rechazaSecretoDebilMenorA256Bits() {
        assertThrows(WeakKeyException.class, () -> new JwtTokenService("clave-muy-corta", 12));
    }

    @Test
    void generaYValidaTokenConSecretoSeguro() {
        var servicio = new JwtTokenService(UUID.randomUUID().toString(), 12);
        String token = servicio.generarToken("supervisor.demo", "SUPERVISOR", "Supervisor de Sucursal");

        var claims = servicio.validarYObtenerClaims(token).orElseThrow();
        assertEquals("supervisor.demo", claims.getSubject());
        assertEquals("SUPERVISOR", claims.get("rol", String.class));
        assertEquals("Supervisor de Sucursal", claims.get("nombre", String.class));
    }
}
