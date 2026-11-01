package ar.com.coto.stock.controller;

import ar.com.coto.stock.dto.Respuestas;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@Tag(name = "Estado", description = "Salud del servicio y punto de entrada.")
public class EstadoController {
    @GetMapping("/api/estado") public Respuestas.Estado estado() { return new Respuestas.Estado("COTO Gestión de Stock", "ACTIVO", "0.3.0"); }
    @GetMapping("/") public Map<String, String> inicio() {
        return Map.of("estado", "/api/estado", "productos", "/api/productos", "combos", "/api/combos",
                "catalogoDigital", "/api/digital/catalogo", "pedidos", "/api/pedidos", "integraciones", "/api/integraciones",
                "documentacion", "/swagger-ui.html");
    }
}
