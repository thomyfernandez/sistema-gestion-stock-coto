package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.EventoIntegracion;
import ar.com.coto.stock.domain.port.RepositorioIntegraciones;
import ar.com.coto.stock.repository.entity.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class RepositorioIntegracionesJpa implements RepositorioIntegraciones {
    @PersistenceContext private EntityManager em;
    @Override public void encolar(EventoIntegracion e) {
        EventoEntity fila = new EventoEntity();
        fila.clave = e.clave(); fila.tipo = e.tipo().name(); fila.stockId = e.stockId(); fila.pedidoId = e.pedidoId();
        fila.disponible = e.disponible(); fila.canal = e.canal(); fila.destino = e.destino(); fila.texto = e.texto(); fila.fecha = e.fecha();
        em.persist(fila);
    }
    @Override public List<Long> pendientes(int limite) {
        return em.createQuery("select e.id from EventoEntity e where e.estado = 'PENDIENTE' order by e.id", Long.class)
                .setMaxResults(limite).getResultList();
    }
    @Override public EventoIntegracion obtenerPendiente(long id) {
        EventoEntity e = em.find(EventoEntity.class, id);
        if (e == null || !"PENDIENTE".equals(e.estado)) return null;
        return new EventoIntegracion(e.id, e.clave, EventoIntegracion.Tipo.valueOf(e.tipo), e.stockId, e.pedidoId,
                e.disponible, e.canal, e.destino, e.texto, e.fecha);
    }
    @Override public void registrarExito(long id) {
        EventoEntity fila = em.find(EventoEntity.class, id);
        fila.estado = "ENVIADO"; fila.intentos++; fila.ultimoError = null;
    }
    @Override public void registrarFallo(long id, String error) {
        EventoEntity fila = em.find(EventoEntity.class, id);
        fila.intentos++; fila.ultimoError = error == null ? "Falla de integración" : error.substring(0, Math.min(error.length(), 500));
    }
    @Override public void bloquearEnvios() {
        if (em.find(BloqueoEntity.class, 2, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new IllegalStateException("Falta inicializar el bloqueo de integraciones");
        }
    }
    @Override public List<EstadoEntrega> listar() {
        return em.createQuery("select e from EventoEntity e order by e.id", EventoEntity.class).getResultList().stream()
                .map(e -> new EstadoEntrega(e.id, e.tipo, e.stockId, e.pedidoId, e.estado, e.intentos, e.ultimoError, e.fecha)).toList();
    }
}
