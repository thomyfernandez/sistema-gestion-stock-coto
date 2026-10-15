package ar.com.coto.stock.controller;

import ar.com.coto.stock.dto.*;
import ar.com.coto.stock.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
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
    @PutMapping("/notificaciones/canal") public Respuestas.Canal configurar(@Valid @RequestBody Solicitudes.ConfiguracionCanal s) {
        notificaciones.setCanal(GestionNotificaciones.Canal.valueOf(s.canal().name()));
        return canal();
    }
}
