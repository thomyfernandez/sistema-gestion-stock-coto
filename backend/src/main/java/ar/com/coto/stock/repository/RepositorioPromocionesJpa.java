package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.EntidadNoEncontradaException;
import ar.com.coto.stock.domain.port.RepositorioPromociones;
import ar.com.coto.stock.domain.valorizacion.Promocion;
import ar.com.coto.stock.repository.entity.PromocionEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public class RepositorioPromocionesJpa implements RepositorioPromociones {
    @PersistenceContext private EntityManager em;
    @Override public List<Promocion> listar(int id) {
        return em.createQuery("select p from PromocionEntity p where p.productoId = :id order by p.orden, p.id", PromocionEntity.class)
                .setParameter("id", id).getResultList().stream().map(this::dominio).toList();
    }
    @Override public Promocion guardar(Promocion p) {
        PromocionEntity fila;
        if (p.id() == 0) fila = new PromocionEntity();
        else {
            fila = em.find(PromocionEntity.class, p.id());
            if (fila == null || fila.productoId != p.productoId()) throw new EntidadNoEncontradaException("Promoción", p.id());
        }
        fila.productoId = p.productoId(); fila.tipo = p.tipo().name(); fila.porcentaje = BigDecimal.valueOf(p.porcentaje());
        fila.orden = p.orden(); fila.activa = p.activa(); fila.medioPago = p.medioPago();
        if (p.id() == 0) em.persist(fila);
        return dominio(fila);
    }
    private Promocion dominio(PromocionEntity p) {
        return new Promocion(p.id, p.productoId, Promocion.Tipo.valueOf(p.tipo), p.porcentaje.doubleValue(), p.orden, p.activa, p.medioPago);
    }
}
