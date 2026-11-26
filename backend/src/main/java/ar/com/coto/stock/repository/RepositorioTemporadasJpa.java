package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.EntidadNoEncontradaException;
import ar.com.coto.stock.domain.port.RepositorioTemporadas;
import ar.com.coto.stock.domain.reposicion.Temporada;
import ar.com.coto.stock.repository.entity.TemporadaEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.time.MonthDay;
import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioTemporadasJpa implements RepositorioTemporadas {
    /** Primero las de artículos, después las de categoría; cada grupo por fecha de inicio. */
    private static final String ORDEN = " order by case when t.categoriaId is null then 0 else 1 end, t.categoriaId, t.desdeMes, t.desdeDia, t.id";
    @PersistenceContext private EntityManager em;

    private static Temporada modelo(TemporadaEntity f) {
        return new Temporada(f.id, f.nombre, List.copyOf(f.stockIds), f.categoriaId, MonthDay.of(f.desdeMes, f.desdeDia),
                MonthDay.of(f.hastaMes, f.hastaDia), f.factor);
    }
    @Override public List<Temporada> listar() {
        return em.createQuery("select distinct t from TemporadaEntity t left join fetch t.stockIds" + ORDEN, TemporadaEntity.class)
                .getResultList().stream().map(RepositorioTemporadasJpa::modelo).toList();
    }
    @Override public List<Temporada> listarPara(int stockId, Integer categoriaId) {
        var consulta = em.createQuery("select distinct t from TemporadaEntity t left join fetch t.stockIds where :stock member of t.stockIds"
                + (categoriaId == null ? "" : " or t.categoriaId = :categoria") + ORDEN, TemporadaEntity.class);
        consulta.setParameter("stock", stockId);
        if (categoriaId != null) consulta.setParameter("categoria", categoriaId);
        return consulta.getResultList().stream().map(RepositorioTemporadasJpa::modelo).toList();
    }
    @Override public Optional<Temporada> obtener(long id) {
        return Optional.ofNullable(em.find(TemporadaEntity.class, id)).map(RepositorioTemporadasJpa::modelo);
    }
    @Override public Temporada guardar(Temporada t) {
        TemporadaEntity fila = t.id() == 0 ? new TemporadaEntity() : em.find(TemporadaEntity.class, t.id());
        if (fila == null) throw new EntidadNoEncontradaException("Temporada", t.id());
        fila.nombre = t.nombre(); fila.categoriaId = t.categoriaId();
        fila.stockIds.clear(); fila.stockIds.addAll(t.stockIds());
        fila.desdeMes = t.desde().getMonthValue(); fila.desdeDia = t.desde().getDayOfMonth();
        fila.hastaMes = t.hasta().getMonthValue(); fila.hastaDia = t.hasta().getDayOfMonth();
        fila.factor = t.factor();
        if (t.id() == 0) em.persist(fila);
        em.flush();
        return modelo(fila);
    }
    @Override public void eliminar(long id) {
        TemporadaEntity fila = em.find(TemporadaEntity.class, id);
        if (fila != null) em.remove(fila);
    }
}
