package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.AuditoriaStock;
import ar.com.coto.stock.domain.model.Pagina;
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
                .map(RepositorioAuditoriaJpa::modelo).toList();
    }
    @Override public Pagina<AuditoriaStock> buscar(Integer stockId, int pagina, int limite) {
        String filtro = stockId == null ? "" : " where a.stockId = :stock";
        var filas = em.createQuery("select a from AuditoriaEntity a" + filtro + " order by a.fecha desc, a.id desc", AuditoriaEntity.class);
        var cantidad = em.createQuery("select count(a) from AuditoriaEntity a" + filtro, Long.class);
        if (stockId != null) { filas.setParameter("stock", stockId); cantidad.setParameter("stock", stockId); }
        var elementos = filas.setFirstResult(Math.multiplyExact(pagina, limite)).setMaxResults(limite)
                .getResultList().stream().map(RepositorioAuditoriaJpa::modelo).toList();
        long total = cantidad.getSingleResult();
        return new Pagina<>(elementos, total);
    }
    private static AuditoriaStock modelo(AuditoriaEntity a) {
        return new AuditoriaStock(a.stockId, a.fecha, a.disponibleAnterior, a.disponibleActual, a.detalle);
    }
}
