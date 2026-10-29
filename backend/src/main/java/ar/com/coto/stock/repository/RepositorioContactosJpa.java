package ar.com.coto.stock.repository;
import ar.com.coto.stock.domain.model.Contacto;
import ar.com.coto.stock.domain.port.RepositorioContactos;
import ar.com.coto.stock.repository.entity.ContactoEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public class RepositorioContactosJpa implements RepositorioContactos {
    @PersistenceContext private EntityManager em;
    private Contacto modelo(ContactoEntity p) { return new Contacto(p.id, p.nombre, p.email, p.telefono); }
    public List<Contacto> listar() {
        return em.createQuery("select p from ContactoEntity p order by p.nombre, p.id", ContactoEntity.class)
                .getResultList().stream().map(this::modelo).toList();
    }
    public Optional<Contacto> obtener(long id) { return Optional.ofNullable(em.find(ContactoEntity.class, id)).map(this::modelo); }
    public Contacto guardar(Contacto p) {
        var f = p.id() == 0 ? new ContactoEntity() : em.find(ContactoEntity.class, p.id());
        if (f == null) throw new IllegalStateException("El contacto ya no existe");
        f.nombre = p.nombre(); f.email = p.email(); f.telefono = p.telefono();
        if (p.id() == 0) em.persist(f);
        return modelo(f);
    }
}
