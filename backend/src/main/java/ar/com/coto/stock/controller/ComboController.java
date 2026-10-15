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
@RequestMapping("/api/combos")
public class ComboController {
    private final GestionInventario inventario;
    public ComboController(GestionInventario inventario) { this.inventario = inventario; }
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
}
