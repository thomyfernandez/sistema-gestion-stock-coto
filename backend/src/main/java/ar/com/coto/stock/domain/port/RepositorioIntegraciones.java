package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.EventoIntegracion;
import java.util.List;

public interface RepositorioIntegraciones {
    record EstadoEntrega(long id, String tipo, int stockId, Long pedidoId, String estado,
                         int intentos, String ultimoError, java.time.Instant fecha) { }
    void encolar(EventoIntegracion evento);
    List<Long> pendientes(int limite);
    EventoIntegracion obtenerPendiente(long id);
    void registrarExito(long id);
    void registrarFallo(long id, String error);
    void bloquearEnvios();
    List<EstadoEntrega> listar();
}
