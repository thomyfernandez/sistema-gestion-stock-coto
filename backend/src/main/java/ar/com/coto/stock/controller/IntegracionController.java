package ar.com.coto.stock.controller;

import ar.com.coto.stock.dto.*;
import ar.com.coto.stock.service.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Integraciones y avisos", description = "Entregas a ERP, mensajería y Coto Digital; canal y configuración general de avisos.")
public class IntegracionController {
    private final GestionIntegraciones integraciones;
    private final GestionNotificaciones notificaciones;
    public IntegracionController(GestionIntegraciones integraciones, GestionNotificaciones notificaciones) {
        this.integraciones = integraciones; this.notificaciones = notificaciones;
    }
    @GetMapping("/integraciones") public Respuestas.Integraciones estado() { return MapeadorDto.integraciones(integraciones.consultarEstado()); }
    @PostMapping("/integraciones/reintentos") public Respuestas.Reintento reintentar() {
        return new Respuestas.Reintento(integraciones.procesarPendientes());
    }
    @GetMapping("/notificaciones/canal") public Respuestas.Canal canal() { return new Respuestas.Canal(notificaciones.getCanal().name()); }
    @GetMapping("/configuracion/avisos") public Respuestas.ConfiguracionAvisos avisos() {
        var c = notificaciones.consultarConfiguracion();
        return new Respuestas.ConfiguracionAvisos(c.canal(), c.email(), c.telefono());
    }
    @PutMapping("/configuracion/avisos") public Respuestas.ConfiguracionAvisos configurarAvisos(@Valid @RequestBody Solicitudes.ConfiguracionAvisos s) {
        notificaciones.configurar(new ar.com.coto.stock.domain.model.ConfiguracionAvisos(s.canal().name(), s.email(), s.telefono()));
        return avisos();
    }
    @PutMapping("/notificaciones/canal") public Respuestas.Canal configurar(@Valid @RequestBody Solicitudes.ConfiguracionCanal s) {
        notificaciones.setCanal(GestionNotificaciones.Canal.valueOf(s.canal().name()));
        return canal();
    }
}
