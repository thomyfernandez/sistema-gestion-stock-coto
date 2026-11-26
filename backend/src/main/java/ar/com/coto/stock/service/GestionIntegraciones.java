package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.port.MonitorSistemasExternos;
import ar.com.coto.stock.domain.port.RepositorioIntegraciones;
import java.util.List;

public interface GestionIntegraciones {
    record Estado(List<RepositorioIntegraciones.EstadoEntrega> entregas, MonitorSistemasExternos.Resumen sistemas) {
        public Estado { entregas = List.copyOf(entregas); }
    }
    Estado consultarEstado();
    int procesarPendientes();
    void simularDisponibilidad(MonitorSistemasExternos.Sistema sistema, boolean disponible);
}
