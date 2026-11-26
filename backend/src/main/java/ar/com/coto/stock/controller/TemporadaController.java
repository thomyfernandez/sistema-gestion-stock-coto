package ar.com.coto.stock.controller;

import ar.com.coto.stock.domain.reposicion.Temporada;
import ar.com.coto.stock.dto.Respuestas;
import ar.com.coto.stock.dto.Solicitudes;
import ar.com.coto.stock.service.GestionTemporadas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.DateTimeException;
import java.time.MonthDay;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Temporadas de reposición", description = "Épocas del año (día y mes, se repiten cada año) en las que la política "
        + "estacional multiplica el objetivo de varios artículos o de toda una categoría. Fuera de temporada el factor es 1.")
public class TemporadaController {
    private final GestionTemporadas temporadas;
    public TemporadaController(GestionTemporadas temporadas) { this.temporadas = temporadas; }

    private static Respuestas.Temporada respuesta(GestionTemporadas.Consulta c) {
        Temporada t = c.temporada();
        return new Respuestas.Temporada(t.id(), t.nombre(), t.stockIds(), t.categoriaId(), t.desde().getDayOfMonth(), t.desde().getMonthValue(),
                t.hasta().getDayOfMonth(), t.hasta().getMonthValue(), t.factor(), t.cruzaElAnio(), c.vigente());
    }
    private static MonthDay dia(int dia, int mes, String campo) {
        try { return MonthDay.of(mes, dia); }
        catch (DateTimeException e) {
            throw new IllegalArgumentException("La fecha %s %02d/%02d no existe".formatted(campo, dia, mes));
        }
    }
    private static Temporada temporada(Solicitudes.Temporada s) {
        return new Temporada(0, s.nombre(), s.stockIds(), s.categoriaId(), dia(s.desdeDia(), s.desdeMes(), "de inicio"),
                dia(s.hastaDia(), s.hastaMes(), "de fin"), s.factor());
    }

    @Operation(summary = "Lista las temporadas e indica cuáles rigen hoy en la sucursal")
    @GetMapping("/temporadas") public List<Respuestas.Temporada> listar() {
        return temporadas.listar().stream().map(TemporadaController::respuesta).toList();
    }
    @Operation(summary = "Crea una temporada para uno o más artículos simples o para una categoría")
    @PostMapping("/temporadas") public ResponseEntity<Respuestas.Temporada> crear(@Valid @RequestBody Solicitudes.Temporada s) {
        var creada = respuesta(temporadas.crear(temporada(s)));
        return ResponseEntity.created(URI.create("/api/temporadas/" + creada.id())).body(creada);
    }
    @Operation(summary = "Reemplaza una temporada existente")
    @PutMapping("/temporadas/{id}") public Respuestas.Temporada editar(@PathVariable @Positive long id, @Valid @RequestBody Solicitudes.Temporada s) {
        return respuesta(temporadas.editar(id, temporada(s)));
    }
    @Operation(summary = "Elimina una temporada; el artículo vuelve a su objetivo base en esas fechas")
    @DeleteMapping("/temporadas/{id}") public ResponseEntity<Void> eliminar(@PathVariable @Positive long id) {
        temporadas.eliminar(id);
        return ResponseEntity.noContent().build();
    }
    @Operation(summary = "Factor estacional que aplica hoy al artículo: el de su temporada, el de su categoría o 1")
    @GetMapping("/productos/{id}/factor-estacional") public Respuestas.FactorEstacional factor(@PathVariable @Positive int id) {
        var v = temporadas.factorVigente(id);
        var t = v.factor().temporada();
        return new Respuestas.FactorEstacional(v.stockId(), v.fecha(), v.factor().factor(), v.factor().origen().name(),
                t == null ? null : t.id(), t == null ? null : t.nombre());
    }
}
