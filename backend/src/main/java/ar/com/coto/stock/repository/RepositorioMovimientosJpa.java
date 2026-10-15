package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.RepositorioMovimientos;
import ar.com.coto.stock.repository.entity.MovimientoEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.List;

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
                .map(m -> new MovimientoRegistrado(m.id, new MovimientoStock(m.stockId, TipoMovimiento.valueOf(m.tipo),
                        m.cantidad, m.fecha, m.motivo, m.usuario))).toList();
    }
}
