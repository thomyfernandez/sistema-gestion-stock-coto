package ar.com.coto.stock.repository;

import ar.com.coto.stock.repository.entity.UsuarioEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Repositorio JPA para la consulta de usuarios y autenticación. */
@Repository
public class RepositorioUsuariosJpa {
    @PersistenceContext
    private EntityManager em;

    public Optional<UsuarioEntity> buscarPorUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try {
            var usuario = em.createQuery(
                    "SELECT u FROM UsuarioEntity u WHERE LOWER(u.username) = LOWER(:username) AND u.activo = TRUE",
                    UsuarioEntity.class)
                    .setParameter("username", username.trim())
                    .getSingleResult();
            return Optional.of(usuario);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}

