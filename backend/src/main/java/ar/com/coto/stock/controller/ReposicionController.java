package ar.com.coto.stock.controller;

import ar.com.coto.stock.dto.*;
import ar.com.coto.stock.service.GestionInventario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReposicionController {
    private final GestionInventario inventario;
    public ReposicionController(GestionInventario inventario) { this.inventario = inventario; }
    @GetMapping("/pedidos") public List<Respuestas.Pedido> pedidos(@RequestParam(required = false) @Positive Integer stockId,
            @RequestParam(required = false) Solicitudes.EstadoPedido estado) {
        return inventario.listarPedidos().stream().map(MapeadorDto::pedido)
                .filter(p -> stockId == null || stockId == p.stockId()).filter(p -> estado == null || estado.name().equals(p.estado())).toList();
    }
    @GetMapping("/pedidos/{id}") public Respuestas.Pedido pedido(@PathVariable @Positive long id) {
        return MapeadorDto.pedido(inventario.consultarPedido(id));
    }
    @PostMapping("/reposicion/evaluaciones") public List<Respuestas.Pedido> evaluar() {
        return inventario.evaluarReposicion().stream().map(MapeadorDto::pedido).toList();
    }
    @PostMapping("/pedidos/{id}/recepcion") public ResponseEntity<Respuestas.ResultadoMovimiento> recibir(@PathVariable @Positive long id,
            @Valid @RequestBody Solicitudes.Usuario s) {
        var resultado = MapeadorDto.resultado(inventario.recibirPedido(id, s.usuario()));
        return ResponseEntity.created(URI.create("/api/movimientos/" + resultado.id())).body(resultado);
    }
    @PostMapping("/pedidos/{id}/cancelacion") public ResponseEntity<Void> cancelar(@PathVariable @Positive long id) {
        inventario.cancelarPedido(id);
        return ResponseEntity.noContent().build();
    }
}
