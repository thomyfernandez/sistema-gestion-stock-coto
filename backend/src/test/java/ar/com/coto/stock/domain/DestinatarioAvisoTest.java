package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.DestinatarioAviso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DestinatarioAvisoTest {

    @Test
    @DisplayName("Crea destinatario para lista general con email normalizado en minúsculas y sin espacios")
    void crearDestinatarioGeneralValido() {
        var destinatario = new DestinatarioAviso(10L, null, "  Compras.Central@COTO.com.ar  ");
        assertEquals(10L, destinatario.id());
        assertNull(destinatario.categoriaId());
        assertEquals("compras.central@coto.com.ar", destinatario.email());
        assertTrue(destinatario.esGeneral());
    }

    @Test
    @DisplayName("Crea destinatario asociado a una categoría específica")
    void crearDestinatarioPorCategoriaValido() {
        var destinatario = new DestinatarioAviso(2, "frescos.almacen@coto.com.ar");
        assertNull(destinatario.id());
        assertEquals(2, destinatario.categoriaId());
        assertEquals("frescos.almacen@coto.com.ar", destinatario.email());
        assertFalse(destinatario.esGeneral());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "usuario",
            "usuario@",
            "@coto.com.ar",
            "usuario@dominio",
            "usuario @coto.com.ar",
            "usuario@ coto.com.ar",
            "",
            "   "
    })
    @DisplayName("Rechaza correos con formato inválido")
    void rechazaEmailsConFormatoInvalido(String emailInvalido) {
        assertThrows(IllegalArgumentException.class, () -> new DestinatarioAviso(null, emailInvalido));
    }

    @Test
    @DisplayName("Rechaza correo nulo")
    void rechazaEmailNulo() {
        assertThrows(IllegalArgumentException.class, () -> new DestinatarioAviso(null, null));
    }

    @Test
    @DisplayName("Rechaza id no positivo")
    void rechazaIdNoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> new DestinatarioAviso(0L, 1, "test@coto.com.ar"));
        assertThrows(IllegalArgumentException.class, () -> new DestinatarioAviso(-5L, 1, "test@coto.com.ar"));
    }

    @Test
    @DisplayName("Rechaza categoriaId no positivo")
    void rechazaCategoriaIdNoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> new DestinatarioAviso(0, "test@coto.com.ar"));
        assertThrows(IllegalArgumentException.class, () -> new DestinatarioAviso(-1, "test@coto.com.ar"));
    }
}
