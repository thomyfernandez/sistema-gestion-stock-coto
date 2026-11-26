package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.EntidadNoEncontradaException;
import ar.com.coto.stock.domain.port.RepositorioPromociones;
import ar.com.coto.stock.domain.valorizacion.Promocion;
import ar.com.coto.stock.repository.entity.PromocionEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class RepositorioPromocionesJpa implements RepositorioPromociones {
    @PersistenceContext private EntityManager em;
    @Override public List<Promocion> listar(int id) {
        return em.createQuery("select p from PromocionEntity p where p.productoId = :id order by p.orden, p.id", PromocionEntity.class)
                .setParameter("id", id).getResultList().stream().map(this::dominio).toList();
    }
    @Override public Map<Integer, List<Promocion>> listarPorProducto() {
        return em.createQuery("select p from PromocionEntity p order by p.productoId, p.orden, p.id", PromocionEntity.class)
                .getResultList().stream().map(this::dominio)
                .collect(Collectors.groupingBy(Promocion::productoId, LinkedHashMap::new, Collectors.toList()));
    }
    @Override public Promocion guardar(Promocion p) {
        PromocionEntity fila;
        if (p.id() == 0) fila = new PromocionEntity();
        else {
            fila = em.find(PromocionEntity.class, p.id());
            if (fila == null || fila.productoId != p.productoId()) throw new EntidadNoEncontradaException("Promoción", p.id());
        }
        fila.productoId = p.productoId(); fila.tipo = p.tipo().name(); fila.porcentaje = p.porcentaje();
        fila.orden = p.orden(); fila.activa = p.activa(); fila.medioPago = p.medioPago();
        if (p.id() == 0) em.persist(fila);
        return dominio(fila);
    }
    @Override public void eliminar(long id) {
        PromocionEntity fila = em.find(PromocionEntity.class, id);
        if (fila == null) throw new EntidadNoEncontradaException("Promoción", id);
        em.remove(fila);
    }
    private Promocion dominio(PromocionEntity p) {
        return new Promocion(p.id, p.productoId, Promocion.Tipo.valueOf(p.tipo), p.porcentaje, p.orden, p.activa, p.medioPago);
    }
}
