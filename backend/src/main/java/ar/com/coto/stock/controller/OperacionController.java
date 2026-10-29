package ar.com.coto.stock.controller;

import ar.com.coto.stock.dto.*;
import ar.com.coto.stock.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Clock;
import java.util.List;

/** Entradas para las vistas operativas; toda modificación sigue pasando por el Singleton. */
@RestController
@RequestMapping("/api")
@Tag(name = "Operaciones de sucursal y Coto Digital", description = "Recepción, movimientos de sucursal, catálogo digital, reservas, cancelaciones y despachos.")
public class OperacionController {
    private final GestionInventario inventario;
    private final ConsultaPrecios precios;
    private final Clock reloj;
    public OperacionController(GestionInventario inventario, ConsultaPrecios precios, Clock reloj) {
        this.inventario = inventario; this.precios = precios; this.reloj = reloj;
    }
    @PostMapping("/recepcion/ingresos") public ResponseEntity<Respuestas.ResultadoMovimiento> recibir(@Valid @RequestBody Solicitudes.Operacion s) {
        return operar(s, Solicitudes.TipoMovimiento.RECEPCION);
    }
    @PostMapping("/sucursal/movimientos") public ResponseEntity<Respuestas.ResultadoMovimiento> mover(@Valid @RequestBody Solicitudes.MovimientoSucursal s) {
        var resultado = MapeadorDto.resultado(inventario.registrarMovimiento(MapeadorDto.movimiento(s, reloj)));
        return ResponseEntity.created(URI.create("/api/movimientos/" + resultado.id())).body(resultado);
    }
    @GetMapping("/digital/catalogo") public List<Respuestas.ItemCatalogo> catalogo(@RequestParam(defaultValue = "1") @Positive int cantidad,
            @RequestParam(required = false) @Size(max = 60) String medioPago) {
        return precios.catalogo(cantidad, medioPago).stream().map(MapeadorDto::catalogo).toList();
    }
    @PostMapping("/digital/reservas") public ResponseEntity<Respuestas.ResultadoMovimiento> reservar(@Valid @RequestBody Solicitudes.Operacion s) {
        return operar(s, Solicitudes.TipoMovimiento.RESERVA_DIGITAL);
    }
    @PostMapping("/digital/cancelaciones") public ResponseEntity<Respuestas.ResultadoMovimiento> cancelar(@Valid @RequestBody Solicitudes.Operacion s) {
        return operar(s, Solicitudes.TipoMovimiento.CANCELACION_RESERVA);
    }
    @PostMapping("/digital/despachos") public ResponseEntity<Respuestas.ResultadoMovimiento> despachar(@Valid @RequestBody Solicitudes.Operacion s) {
        return operar(s, Solicitudes.TipoMovimiento.DESPACHO_DIGITAL);
    }
    private ResponseEntity<Respuestas.ResultadoMovimiento> operar(Solicitudes.Operacion s, Solicitudes.TipoMovimiento tipo) {
        var resultado = MapeadorDto.resultado(inventario.registrarMovimiento(MapeadorDto.movimiento(s, tipo, reloj)));
        return ResponseEntity.created(URI.create("/api/movimientos/" + resultado.id())).body(resultado);
    }
}
