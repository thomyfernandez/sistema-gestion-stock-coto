package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.port.MonitorSistemasExternos;
import ar.com.coto.stock.domain.port.RepositorioIntegraciones;
import java.util.List;

public interface GestionIntegraciones {
    enum Sistema { ERP, MENSAJERIA, COTO_DIGITAL }
    record Estado(List<RepositorioIntegraciones.EstadoEntrega> entregas, MonitorSistemasExternos.Resumen sistemas) {
        public Estado { entregas = List.copyOf(entregas); }
    }
    Estado consultarEstado();
    int procesarPendientes();
    void simularDisponibilidad(Sistema sistema, boolean disponible);
}
