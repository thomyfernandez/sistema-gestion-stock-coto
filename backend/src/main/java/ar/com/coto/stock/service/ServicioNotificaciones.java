package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.notificacion.CanalNotificador;
import java.util.Map;
import java.util.Objects;
import ar.com.coto.stock.domain.model.Canal;
import ar.com.coto.stock.domain.model.ConfiguracionAvisos;
import ar.com.coto.stock.domain.port.RepositorioAvisos;
import ar.com.coto.stock.domain.port.UnidadDeTrabajo;

/** Context de Strategy. Los mensajes encolados conservan el canal elegido al crearse. */
public final class ServicioNotificaciones implements GestionNotificaciones {
    private final Map<Canal, CanalNotificador> canales;
    private final RepositorioAvisos configuracion;
    private final UnidadDeTrabajo unidad;
    public ServicioNotificaciones(Map<Canal, CanalNotificador> canales, RepositorioAvisos configuracion, UnidadDeTrabajo unidad) {
        this.configuracion = configuracion; this.unidad = unidad;
        this.canales = Map.copyOf(canales);
        for (Canal c : Canal.values()) Objects.requireNonNull(this.canales.get(c), "Falta canal " + c);
    }
    public void setCanal(Canal nuevo) {
        unidad.ejecutar(() -> { var c = configuracion.obtener(); configuracion.guardar(new ConfiguracionAvisos(Objects.requireNonNull(nuevo), c.email(), c.telefono())); return null; });
    }
    public Canal getCanal() { return consultarConfiguracion().canal(); }
    public ConfiguracionAvisos consultarConfiguracion() { return unidad.consultar(configuracion::obtener); }
    public void configurar(ConfiguracionAvisos c) { unidad.ejecutar(() -> { configuracion.guardar(c); return null; }); }
    public void enviar(Canal canal, String destino, String texto) { canales.get(Objects.requireNonNull(canal)).enviar(destino, texto); }
}
