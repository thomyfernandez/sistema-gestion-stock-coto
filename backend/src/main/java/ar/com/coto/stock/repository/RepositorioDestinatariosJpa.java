package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.DestinatarioAviso;
import ar.com.coto.stock.domain.port.RepositorioDestinatarios;
import ar.com.coto.stock.repository.entity.DestinatarioAvisoEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class RepositorioDestinatariosJpa implements RepositorioDestinatarios {
    @PersistenceContext
    private EntityManager em;

    private DestinatarioAviso modelo(DestinatarioAvisoEntity fila) {
        return new DestinatarioAviso(fila.id, fila.categoriaId, fila.email);
    }

    @Override
    public List<DestinatarioAviso> listarGenerales() {
        return em.createQuery("select d from DestinatarioAvisoEntity d where d.categoriaId is null order by lower(d.email)", DestinatarioAvisoEntity.class)
                .getResultList()
                .stream()
                .map(this::modelo)
                .toList();
    }

    @Override
    public List<DestinatarioAviso> listarPorCategoria(int categoriaId) {
        return em.createQuery("select d from DestinatarioAvisoEntity d where d.categoriaId = :catId order by lower(d.email)", DestinatarioAvisoEntity.class)
                .setParameter("catId", categoriaId)
                .getResultList()
                .stream()
                .map(this::modelo)
                .toList();
    }

    @Override
    public List<DestinatarioAviso> listarTodos() {
        return em.createQuery("select d from DestinatarioAvisoEntity d order by d.categoriaId, lower(d.email)", DestinatarioAvisoEntity.class)
                .getResultList()
                .stream()
                .map(this::modelo)
                .toList();
    }

    @Override
    public void guardarGenerales(List<String> emails) {
        em.createQuery("delete from DestinatarioAvisoEntity d where d.categoriaId is null").executeUpdate();
        if (emails != null) {
            Set<String> normalizados = new LinkedHashSet<>();
            for (String e : emails) {
                if (e != null && !e.isBlank()) {
                    normalizados.add(e.strip().toLowerCase(Locale.ROOT));
                }
            }
            for (String email : normalizados) {
                em.persist(new DestinatarioAvisoEntity(null, email));
            }
        }
    }

    @Override
    public void guardarPorCategoria(int categoriaId, List<String> emails) {
        em.createQuery("delete from DestinatarioAvisoEntity d where d.categoriaId = :catId")
                .setParameter("catId", categoriaId)
                .executeUpdate();
        if (emails != null) {
            Set<String> normalizados = new LinkedHashSet<>();
            for (String e : emails) {
                if (e != null && !e.isBlank()) {
                    normalizados.add(e.strip().toLowerCase(Locale.ROOT));
                }
            }
            for (String email : normalizados) {
                em.persist(new DestinatarioAvisoEntity(categoriaId, email));
            }
        }
    }
}
