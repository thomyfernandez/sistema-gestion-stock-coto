package ar.com.coto.stock.controller;

import ar.com.coto.stock.domain.model.TipoMovimiento;
import ar.com.coto.stock.dto.*;
import ar.com.coto.stock.service.GestionInventario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
@Tag(name = "Movimientos y auditoría", description = "Historial de movimientos de stock y auditoría de cambios.")
public class MovimientoController {
    static final String TOTAL = "X-Total-Count";
    private final GestionInventario inventario;
    private final Clock reloj;
    public MovimientoController(GestionInventario inventario, Clock reloj) { this.inventario = inventario; this.reloj = reloj; }
    @GetMapping("/movimientos") public ResponseEntity<List<Respuestas.Movimiento>> listar(@RequestParam(required = false) @Positive Integer stockId,
            @Parameter(description = "Uno o más tipos, separados por coma o repetidos.") @RequestParam(required = false) List<Solicitudes.TipoMovimiento> tipo,
            @Parameter(description = "Con límite, se pagina del más reciente al más antiguo e informa el total en X-Total-Count.") @RequestParam(required = false) @Min(1) @Max(500) Integer limite,
            @RequestParam(defaultValue = "0") @PositiveOrZero @Max(1_000_000) int pagina) {
        Set<TipoMovimiento> tipos = tipo == null || tipo.isEmpty() ? Set.of()
                : EnumSet.copyOf(tipo.stream().map(t -> TipoMovimiento.valueOf(t.name())).toList());
        if (limite == null) {
            return ResponseEntity.ok(inventario.listarMovimientos().stream().map(MapeadorDto::movimiento)
                    .filter(m -> stockId == null || stockId == m.stockId()).filter(m -> tipos.isEmpty() || tipos.contains(TipoMovimiento.valueOf(m.tipo()))).toList());
        }
        var resultado = inventario.buscarMovimientos(stockId, tipos, pagina, limite);
        return ResponseEntity.ok().header(TOTAL, String.valueOf(resultado.total()))
                .body(resultado.elementos().stream().map(MapeadorDto::movimiento).toList());
    }
    @GetMapping("/movimientos/{id}") public Respuestas.Movimiento obtener(@PathVariable @Positive long id) {
        return MapeadorDto.movimiento(inventario.consultarMovimiento(id));
    }
    @PostMapping("/movimientos") public ResponseEntity<Respuestas.ResultadoMovimiento> registrar(@Valid @RequestBody Solicitudes.Movimiento s) {
        var resultado = MapeadorDto.resultado(inventario.registrarMovimiento(MapeadorDto.movimiento(s, reloj)));
        return ResponseEntity.created(URI.create("/api/movimientos/" + resultado.id())).body(resultado);
    }
    @GetMapping("/auditoria") public ResponseEntity<List<Respuestas.Auditoria>> auditoria(@RequestParam(required = false) @Positive Integer stockId,
            @Parameter(description = "Con límite, se pagina del más reciente al más antiguo e informa el total en X-Total-Count.") @RequestParam(required = false) @Min(1) @Max(500) Integer limite,
            @RequestParam(defaultValue = "0") @PositiveOrZero @Max(1_000_000) int pagina) {
        if (limite == null) {
            return ResponseEntity.ok(inventario.listarAuditoria().stream().map(MapeadorDto::auditoria).filter(a -> stockId == null || stockId == a.stockId()).toList());
        }
        var resultado = inventario.buscarAuditoria(stockId, pagina, limite);
        return ResponseEntity.ok().header(TOTAL, String.valueOf(resultado.total()))
                .body(resultado.elementos().stream().map(MapeadorDto::auditoria).toList());
    }
}
