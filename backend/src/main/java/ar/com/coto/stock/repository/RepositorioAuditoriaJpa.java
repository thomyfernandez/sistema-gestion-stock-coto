package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.AuditoriaStock;
import ar.com.coto.stock.domain.port.RepositorioAuditoria;
import ar.com.coto.stock.repository.entity.AuditoriaEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class RepositorioAuditoriaJpa implements RepositorioAuditoria {
    @PersistenceContext private EntityManager em;
    @Override public void guardar(AuditoriaStock a, Long movimientoId) {
        AuditoriaEntity fila = new AuditoriaEntity();
        fila.stockId = a.stockId(); fila.fecha = a.fecha(); fila.movimientoId = movimientoId;
        fila.disponibleAnterior = a.disponibleAnterior(); fila.disponibleActual = a.disponibleActual(); fila.detalle = a.detalle();
        em.persist(fila);
    }
    @Override public List<AuditoriaStock> listar() {
        return em.createQuery("select a from AuditoriaEntity a order by a.id", AuditoriaEntity.class).getResultList().stream()
                .map(a -> new AuditoriaStock(a.stockId, a.fecha, a.disponibleAnterior, a.disponibleActual, a.detalle)).toList();
    }
}
