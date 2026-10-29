package ar.com.coto.stock.controller;
import ar.com.coto.stock.domain.model.Contacto;
import ar.com.coto.stock.dto.Solicitudes;
import ar.com.coto.stock.service.GestionContactos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController @RequestMapping({"/api/configuracion/contactos", "/api/configuracion/proveedores"})
@Tag(name = "Contactos", description = "Agenda de contactos internos que reciben avisos de stock.")
public class ContactoController {
    private final GestionContactos contactos;
    public ContactoController(GestionContactos contactos) { this.contactos = contactos; }
    @GetMapping public List<Contacto> listar() { return contactos.listar(); }
    @GetMapping("/{id}") public Contacto consultar(@PathVariable @Positive long id) { return contactos.consultar(id); }
    @PostMapping public ResponseEntity<Contacto> crear(@Valid @RequestBody Solicitudes.Contacto p) {
        var creado = contactos.guardar(new Contacto(0, p.nombre(), p.email(), p.telefono()));
        return ResponseEntity.created(URI.create("/api/configuracion/contactos/" + creado.id())).body(creado);
    }
    @PutMapping("/{id}") public Contacto editar(@PathVariable @Positive long id, @Valid @RequestBody Solicitudes.Contacto p) {
        return contactos.guardar(new Contacto(id, p.nombre(), p.email(), p.telefono()));
    }
}
