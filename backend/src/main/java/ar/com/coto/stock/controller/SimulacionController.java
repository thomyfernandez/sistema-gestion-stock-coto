package ar.com.coto.stock.controller;

import ar.com.coto.stock.dto.*;
import ar.com.coto.stock.service.GestionIntegraciones;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** Sólo existe en demo. Opera sobre el contrato de aplicación y los proveedores simulados. */
@RestController
@Profile("demo")
@RequestMapping("/api/demo/proveedores")
public class SimulacionController {
    private final GestionIntegraciones integraciones;
    public SimulacionController(GestionIntegraciones integraciones) { this.integraciones = integraciones; }
    @PatchMapping("/{sistema}") public Respuestas.EstadoProveedor configurar(@PathVariable Solicitudes.Sistema sistema,
            @Valid @RequestBody Solicitudes.Disponibilidad s) {
        integraciones.simularDisponibilidad(GestionIntegraciones.Sistema.valueOf(sistema.name()), s.disponible());
        return new Respuestas.EstadoProveedor(sistema.name(), s.disponible());
    }
}
