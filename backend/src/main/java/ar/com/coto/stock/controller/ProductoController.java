package ar.com.coto.stock.controller;

import ar.com.coto.stock.dto.*;
import ar.com.coto.stock.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {
    private final GestionInventario inventario;
    private final ConsultaPrecios precios;
    public ProductoController(GestionInventario inventario, ConsultaPrecios precios) { this.inventario = inventario; this.precios = precios; }

    @GetMapping public List<Respuestas.Producto> listar(@RequestParam(required = false) Solicitudes.TipoProducto tipo, @RequestParam(required = false) @Positive Integer categoriaId) {
        return inventario.listarStock().stream().map(MapeadorDto::producto).filter(p -> tipo == null || tipo.name().equals(p.tipo())).filter(p -> categoriaId == null || categoriaId.equals(p.categoriaId())).toList();
    }
    @GetMapping("/{id}") public Respuestas.Producto obtener(@PathVariable @Positive int id) {
        return MapeadorDto.producto(inventario.consultarStock(id));
    }
    @GetMapping("/{id}/imagen") public ResponseEntity<byte[]> imagen(@PathVariable @Positive int id) {
        var imagen = inventario.consultarImagen(id);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(imagen.tipo()))
                .cacheControl(org.springframework.http.CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff").body(imagen.contenido());
    }
    @PostMapping public ResponseEntity<Respuestas.Producto> crear(@Valid @RequestBody Solicitudes.Producto solicitud) {
        var producto = MapeadorDto.producto(inventario.crearProducto(MapeadorDto.alta(solicitud)));
        return ResponseEntity.created(URI.create("/api/productos/" + producto.id())).body(producto);
    }
    @PutMapping("/{id}/categoria") public Respuestas.Producto categoria(@PathVariable @Positive int id, @Valid @RequestBody Solicitudes.CategoriaProducto s) {
        inventario.asignarCategoria(id, s.categoriaId());
        return MapeadorDto.producto(inventario.consultarStock(id));
    }
    @GetMapping("/{id}/precio") public Respuestas.Precio precio(@PathVariable @Positive int id,
            @RequestParam(defaultValue = "1") @Positive int cantidad, @RequestParam(required = false) @Size(max = 60) String medioPago) {
        return MapeadorDto.precio(precios.valorizar(id, cantidad, medioPago));
    }
    @GetMapping("/{id}/politica-reposicion") public Respuestas.Politica politica(@PathVariable @Positive int id) {
        return MapeadorDto.politica(inventario.consultarPolitica(id));
    }
    @PutMapping("/{id}/politica-reposicion") public Respuestas.Politica configurar(@PathVariable @Positive int id,
            @Valid @RequestBody Solicitudes.Politica solicitud) {
        inventario.configurarPolitica(MapeadorDto.politica(id, solicitud));
        return MapeadorDto.politica(inventario.consultarPolitica(id));
    }
    @GetMapping("/{id}/promociones") public List<Respuestas.Promocion> promociones(@PathVariable @Positive int id) {
        return inventario.listarPromociones(id).stream().map(MapeadorDto::promocion).toList();
    }
    @GetMapping("/{id}/promociones/{promocionId}") public Respuestas.Promocion promocion(@PathVariable @Positive int id,
            @PathVariable @Positive long promocionId) {
        return MapeadorDto.promocion(inventario.consultarPromocion(id, promocionId));
    }
    @PostMapping("/{id}/promociones") public ResponseEntity<Respuestas.Promocion> crearPromocion(@PathVariable @Positive int id,
            @Valid @RequestBody Solicitudes.Promocion solicitud) {
        var promocion = MapeadorDto.promocion(inventario.guardarPromocion(MapeadorDto.promocion(id, 0, solicitud)));
        return ResponseEntity.created(URI.create("/api/productos/" + id + "/promociones/" + promocion.id())).body(promocion);
    }
    @PutMapping("/{id}/promociones/{promocionId}") public Respuestas.Promocion modificarPromocion(@PathVariable @Positive int id,
            @PathVariable @Positive long promocionId, @Valid @RequestBody Solicitudes.Promocion solicitud) {
        return MapeadorDto.promocion(inventario.guardarPromocion(MapeadorDto.promocion(id, promocionId, solicitud)));
    }
}
