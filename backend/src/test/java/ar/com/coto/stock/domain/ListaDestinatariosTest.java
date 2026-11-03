package ar.com.coto.stock.domain;

import ar.com.coto.stock.domain.model.ListaDestinatarios;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ListaDestinatariosTest {
    @Test void normalizaYQuitaDuplicadosConservandoElOrden() {
        var lista = new ListaDestinatarios(3, List.of(" Jefe@COTO.example", "", "deposito@coto.example", "jefe@coto.example"));
        assertEquals(List.of("jefe@coto.example", "deposito@coto.example"), lista.emails());
        assertFalse(lista.esGeneral());
        assertTrue(ListaDestinatarios.general(List.of()).vacia());
    }
    @Test void rechazaEmailsInvalidosCategoriasInvalidasYListasExtensas() {
        assertThrows(IllegalArgumentException.class, () -> ListaDestinatarios.general(List.of("sin-arroba")));
        assertThrows(IllegalArgumentException.class, () -> ListaDestinatarios.general(List.of("a b@coto.example")));
        assertThrows(IllegalArgumentException.class, () -> new ListaDestinatarios(0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ListaDestinatarios.general(
                IntStream.rangeClosed(1, ListaDestinatarios.MAXIMO + 1).mapToObj(i -> "u" + i + "@coto.example").toList()));
        assertEquals(ListaDestinatarios.MAXIMO, ListaDestinatarios.general(
                IntStream.rangeClosed(1, ListaDestinatarios.MAXIMO).mapToObj(i -> "u" + i + "@coto.example").toList()).emails().size());
    }
}
