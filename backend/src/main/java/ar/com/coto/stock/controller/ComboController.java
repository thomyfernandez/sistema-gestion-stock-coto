package ar.com.coto.stock.controller;

import ar.com.coto.stock.domain.model.MovimientoStock;
import ar.com.coto.stock.domain.model.TipoMovimiento;
import ar.com.coto.stock.dto.*;
import ar.com.coto.stock.service.GestionInventario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/combos")
@Tag(name = "Combos", description = "Alta, consulta, armado y desarmado de combos promocionales con stock propio o virtual.")
public class ComboController {
    private final GestionInventario inventario;
    private final Clock reloj;

    public ComboController(GestionInventario inventario, Clock reloj) {
        this.inventario = inventario;
        this.reloj = reloj;
    }

    @GetMapping public List<Respuestas.Producto> listar() {
        return inventario.listarStock().stream().map(MapeadorDto::producto).filter(p -> "COMBO".equals(p.tipo())).toList();
    }

    @GetMapping("/{id}") public Respuestas.Producto obtener(@PathVariable @Positive int id) {
        return MapeadorDto.producto(inventario.consultarCombo(id));
    }

    @PostMapping public ResponseEntity<Respuestas.Producto> crear(@Valid @RequestBody Solicitudes.Combo solicitud) {
        var combo = MapeadorDto.producto(inventario.crearCombo(MapeadorDto.alta(solicitud), MapeadorDto.componentes(solicitud)));
        return ResponseEntity.created(URI.create("/api/combos/" + combo.id())).body(combo);
    }

    @PostMapping("/{id}/armado")
    public ResponseEntity<Respuestas.ResultadoMovimiento> armar(
            @PathVariable @Positive int id,
            @Valid @RequestBody Solicitudes.OperacionCombo solicitud) {
        MovimientoStock movimiento = new MovimientoStock(id, TipoMovimiento.ARMADO_COMBO,
                solicitud.cantidad(), reloj.instant(), solicitud.motivo(), solicitud.usuario());
        var resultado = MapeadorDto.resultado(inventario.registrarMovimiento(movimiento));
        return ResponseEntity.created(URI.create("/api/movimientos/" + resultado.id())).body(resultado);
    }

    @PostMapping("/{id}/desarmado")
    public ResponseEntity<Respuestas.ResultadoMovimiento> desarmar(
            @PathVariable @Positive int id,
            @Valid @RequestBody Solicitudes.OperacionCombo solicitud) {
        MovimientoStock movimiento = new MovimientoStock(id, TipoMovimiento.DESARMADO_COMBO,
                solicitud.cantidad(), reloj.instant(), solicitud.motivo(), solicitud.usuario());
        var resultado = MapeadorDto.resultado(inventario.registrarMovimiento(movimiento));
        return ResponseEntity.created(URI.create("/api/movimientos/" + resultado.id())).body(resultado);
    }
}
