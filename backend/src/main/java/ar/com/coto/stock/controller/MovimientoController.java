package ar.com.coto.stock.controller;

import ar.com.coto.stock.dto.*;
import ar.com.coto.stock.service.GestionInventario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api")
public class MovimientoController {
    private final GestionInventario inventario;
    private final Clock reloj;
    public MovimientoController(GestionInventario inventario, Clock reloj) { this.inventario = inventario; this.reloj = reloj; }
    @GetMapping("/movimientos") public List<Respuestas.Movimiento> listar(@RequestParam(required = false) @Positive Integer stockId) {
        return inventario.listarMovimientos().stream().map(MapeadorDto::movimiento).filter(m -> stockId == null || stockId == m.stockId()).toList();
    }
    @GetMapping("/movimientos/{id}") public Respuestas.Movimiento obtener(@PathVariable @Positive long id) {
        return MapeadorDto.movimiento(inventario.consultarMovimiento(id));
    }
    @PostMapping("/movimientos") public ResponseEntity<Respuestas.ResultadoMovimiento> registrar(@Valid @RequestBody Solicitudes.Movimiento s) {
        var resultado = MapeadorDto.resultado(inventario.registrarMovimiento(MapeadorDto.movimiento(s, reloj)));
        return ResponseEntity.created(URI.create("/api/movimientos/" + resultado.id())).body(resultado);
    }
    @GetMapping("/auditoria") public List<Respuestas.Auditoria> auditoria(@RequestParam(required = false) @Positive Integer stockId) {
        return inventario.listarAuditoria().stream().map(MapeadorDto::auditoria).filter(a -> stockId == null || stockId == a.stockId()).toList();
    }
}
