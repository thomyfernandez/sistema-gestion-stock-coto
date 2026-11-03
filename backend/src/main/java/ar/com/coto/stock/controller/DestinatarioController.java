package ar.com.coto.stock.controller;
import ar.com.coto.stock.domain.model.ListaDestinatarios;
import ar.com.coto.stock.dto.Respuestas;
import ar.com.coto.stock.dto.Solicitudes;
import ar.com.coto.stock.service.GestionDestinatarios;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/configuracion/destinatarios")
@Tag(name = "Destinatarios de avisos", description = "Listas de emails que reciben los avisos de stock bajo, por categoría y generales.")
public class DestinatarioController {
    private final GestionDestinatarios destinatarios;
    public DestinatarioController(GestionDestinatarios destinatarios) { this.destinatarios = destinatarios; }
    private static Respuestas.Destinatarios respuesta(ListaDestinatarios l) { return new Respuestas.Destinatarios(l.categoriaId(), l.emails()); }
    @GetMapping public List<Respuestas.Destinatarios> listar() {
        return destinatarios.listar().stream().map(DestinatarioController::respuesta).toList();
    }
    @PutMapping("/general") public Respuestas.Destinatarios general(@Valid @RequestBody Solicitudes.Destinatarios s) {
        return respuesta(destinatarios.guardar(ListaDestinatarios.general(s.emails())));
    }
    @PutMapping("/categorias/{categoriaId}") public Respuestas.Destinatarios categoria(@PathVariable @Positive int categoriaId,
            @Valid @RequestBody Solicitudes.Destinatarios s) {
        return respuesta(destinatarios.guardar(new ListaDestinatarios(categoriaId, s.emails())));
    }
}
