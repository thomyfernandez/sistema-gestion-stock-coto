package ar.com.coto.stock.controller;
import ar.com.coto.stock.domain.model.Categoria;
import ar.com.coto.stock.dto.Solicitudes;
import ar.com.coto.stock.service.GestionCategorias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
@RestController @RequestMapping("/api/categorias")
public class CategoriaController {
    private final GestionCategorias categorias;
    public CategoriaController(GestionCategorias categorias) { this.categorias = categorias; }
    @GetMapping public List<Categoria> listar() { return categorias.listar(); }
    @GetMapping("/{id}") public Categoria consultar(@PathVariable @Positive int id) { return categorias.consultar(id); }
    @PostMapping public ResponseEntity<Categoria> crear(@Valid @RequestBody Solicitudes.Categoria c) {
        var creado = categorias.guardar(new Categoria(0, c.nombre(), c.contactoId(), c.canalAlerta() == null ? null : c.canalAlerta().name()));
        return ResponseEntity.created(URI.create("/api/categorias/" + creado.id())).body(creado);
    }
    @PutMapping("/{id}") public Categoria editar(@PathVariable @Positive int id, @Valid @RequestBody Solicitudes.Categoria c) {
        return categorias.guardar(new Categoria(id, c.nombre(), c.contactoId(), c.canalAlerta() == null ? null : c.canalAlerta().name()));
    }
}
