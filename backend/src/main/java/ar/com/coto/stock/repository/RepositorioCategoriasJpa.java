package ar.com.coto.stock.repository;
import ar.com.coto.stock.domain.model.EntidadNoEncontradaException;
import ar.com.coto.stock.domain.model.Canal;
import ar.com.coto.stock.domain.model.Categoria;
import ar.com.coto.stock.domain.port.RepositorioCategorias;
import ar.com.coto.stock.repository.entity.CategoriaEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class RepositorioCategoriasJpa implements RepositorioCategorias {
    @PersistenceContext private EntityManager em;
    private Categoria modelo(CategoriaEntity f) { return new Categoria(f.id, f.nombre, f.contactoId, f.canalAlerta == null ? null : Canal.valueOf(f.canalAlerta)); }
    public List<Categoria> listar() { return em.createQuery("select c from CategoriaEntity c order by c.nombre, c.id", CategoriaEntity.class).getResultList().stream().map(this::modelo).toList(); }
    public Optional<Categoria> obtener(int id) { return Optional.ofNullable(em.find(CategoriaEntity.class, id)).map(this::modelo); }
    public Categoria guardar(Categoria c) {
        var f = c.id() == 0 ? new CategoriaEntity() : em.find(CategoriaEntity.class, c.id());
        if (f == null) throw new EntidadNoEncontradaException("Categoría", c.id());
        f.nombre = c.nombre(); f.nombreClave = c.nombre().toLowerCase(Locale.ROOT);
        f.contactoId = c.contactoId(); f.canalAlerta = c.canalAlerta() == null ? null : c.canalAlerta().name();
        if (c.id() == 0) em.persist(f);
        return modelo(f);
    }
}
