package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.RepositorioMovimientos;
import ar.com.coto.stock.repository.entity.MovimientoEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;

@Repository
public class RepositorioMovimientosJpa implements RepositorioMovimientos {
    @PersistenceContext private EntityManager em;
    @Override public long guardar(MovimientoStock m) {
        MovimientoEntity fila = new MovimientoEntity();
        fila.stockId = m.getStockId(); fila.tipo = m.getTipo().name(); fila.cantidad = m.getCantidad();
        fila.fecha = m.getFecha(); fila.motivo = m.getMotivo(); fila.usuario = m.getUsuario();
        em.persist(fila);
        return fila.id;
    }
    @Override public List<MovimientoRegistrado> listar() {
        return em.createQuery("select m from MovimientoEntity m order by m.id", MovimientoEntity.class).getResultList().stream()
                .map(RepositorioMovimientosJpa::modelo).toList();
    }
    @Override public Pagina<MovimientoRegistrado> buscar(Integer stockId, Set<TipoMovimiento> tipos, int pagina, int limite) {
        boolean porTipo = tipos != null && !tipos.isEmpty();
        String filtro = " where 1 = 1" + (stockId != null ? " and m.stockId = :stock" : "") + (porTipo ? " and m.tipo in :tipos" : "");
        var filas = em.createQuery("select m from MovimientoEntity m" + filtro + " order by m.fecha desc, m.id desc", MovimientoEntity.class);
        var total = em.createQuery("select count(m) from MovimientoEntity m" + filtro, Long.class);
        for (var consulta : List.<TypedQuery<?>>of(filas, total)) {
            if (stockId != null) consulta.setParameter("stock", stockId);
            if (porTipo) consulta.setParameter("tipos", tipos.stream().map(Enum::name).toList());
        }
        var elementos = filas.setFirstResult(Math.multiplyExact(pagina, limite)).setMaxResults(limite).getResultList().stream()
                .map(RepositorioMovimientosJpa::modelo).toList();
        return new Pagina<>(elementos, total.getSingleResult());
    }
    private static MovimientoRegistrado modelo(MovimientoEntity m) {
        return new MovimientoRegistrado(m.id, new MovimientoStock(m.stockId, TipoMovimiento.valueOf(m.tipo), m.cantidad, m.fecha, m.motivo, m.usuario));
    }
}
