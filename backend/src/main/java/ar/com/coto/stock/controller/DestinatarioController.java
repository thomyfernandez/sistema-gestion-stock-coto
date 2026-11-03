package ar.com.coto.stock.controller;

import ar.com.coto.stock.dto.Respuestas;
import ar.com.coto.stock.dto.Solicitudes;
import ar.com.coto.stock.service.GestionDestinatarios;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configuracion/destinatarios")
@Tag(name = "Destinatarios de avisos", description = "Listas de correos electrónicos para alertas automáticas de reposición.")
public class DestinatarioController {
    private final GestionDestinatarios destinatarios;

    public DestinatarioController(GestionDestinatarios destinatarios) {
        this.destinatarios = destinatarios;
    }

    @GetMapping
    public Respuestas.DestinatariosAvisos listar() {
        return new Respuestas.DestinatariosAvisos(
                destinatarios.listarGenerales(),
                destinatarios.listarPorCategoria()
        );
    }

    @PutMapping("/general")
    public List<String> actualizarGeneral(@Valid @RequestBody Solicitudes.Destinatarios request) {
        List<String> emails = request != null && request.emails() != null ? request.emails() : List.of();
        destinatarios.guardarGenerales(emails);
        return destinatarios.listarGenerales();
    }

    @PutMapping("/categorias/{categoriaId}")
    public List<String> actualizarCategoria(
            @PathVariable @Positive int categoriaId,
            @Valid @RequestBody Solicitudes.Destinatarios request) {
        List<String> emails = request != null && request.emails() != null ? request.emails() : List.of();
        destinatarios.guardarPorCategoria(categoriaId, emails);
        return destinatarios.listarPorCategoria().getOrDefault(categoriaId, List.of());
    }
}
