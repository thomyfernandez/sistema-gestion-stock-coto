package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.RepositorioPedidos;
import ar.com.coto.stock.repository.entity.PedidoEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;

@Repository
public class RepositorioPedidosJpa implements RepositorioPedidos {
    @PersistenceContext private EntityManager em;
    @Override public PedidoRegistrado guardar(PedidoReposicion pedido) {
        PedidoEntity fila = new PedidoEntity();
        fila.stockId = pedido.getStockId(); fila.fecha = pedido.getFecha().toInstant();
        fila.cantidad = pedido.getCantidad(); fila.estado = pedido.getEstado().name();
        em.persist(fila);
        return new PedidoRegistrado(fila.id, pedido);
    }
    @Override public PedidoRegistrado obtener(long id) {
        PedidoEntity fila = em.find(PedidoEntity.class, id);
        if (fila == null) throw new EntidadNoEncontradaException("Pedido", id);
        return dominio(fila);
    }
    @Override public void actualizar(PedidoRegistrado pedido) {
        PedidoEntity fila = em.find(PedidoEntity.class, pedido.id());
        if (fila == null) throw new EntidadNoEncontradaException("Pedido", pedido.id());
        fila.estado = pedido.pedido().getEstado().name();
    }
    @Override public int cantidadPendiente(int stockId) {
        Long total = em.createQuery("select coalesce(sum(p.cantidad), 0) from PedidoEntity p where p.stockId = :id and p.estado in ('PENDIENTE', 'ENVIADO')", Long.class)
                .setParameter("id", stockId).getSingleResult();
        return Math.toIntExact(total);
    }
    @Override public List<PedidoRegistrado> listar() {
        return em.createQuery("select p from PedidoEntity p order by p.id", PedidoEntity.class).getResultList().stream().map(this::dominio).toList();
    }
    private PedidoRegistrado dominio(PedidoEntity fila) {
        PedidoReposicion p = new PedidoReposicion(fila.stockId, Date.from(fila.fecha), fila.cantidad);
        switch (EstadoPedido.valueOf(fila.estado)) {
            case PENDIENTE -> { }
            case ENVIADO -> p.marcarEnviado();
            case RECIBIDO -> { p.marcarEnviado(); p.marcarRecibido(); }
            case CANCELADO -> p.cancelar();
        }
        return new PedidoRegistrado(fila.id, p);
    }
}
