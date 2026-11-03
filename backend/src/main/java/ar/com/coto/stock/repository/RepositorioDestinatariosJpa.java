package ar.com.coto.stock.repository;
import ar.com.coto.stock.domain.model.ListaDestinatarios;
import ar.com.coto.stock.domain.port.RepositorioDestinatarios;
import ar.com.coto.stock.repository.entity.DestinatarioEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public class RepositorioDestinatariosJpa implements RepositorioDestinatarios {
    @PersistenceContext private EntityManager em;
    public List<ListaDestinatarios> listar() {
        Map<Integer, List<String>> porCategoria = new LinkedHashMap<>();
        List<String> general = new ArrayList<>();
        em.createQuery("select d from DestinatarioEntity d order by d.categoriaId nulls first, d.id", DestinatarioEntity.class)
                .getResultList().forEach(d -> (d.categoriaId == null ? general : porCategoria.computeIfAbsent(d.categoriaId, k -> new ArrayList<>())).add(d.email));
        var listas = new ArrayList<ListaDestinatarios>();
        listas.add(ListaDestinatarios.general(general));
        porCategoria.forEach((id, emails) -> listas.add(new ListaDestinatarios(id, emails)));
        return listas;
    }
    public ListaDestinatarios obtener(Integer categoriaId) {
        var consulta = em.createQuery(categoriaId == null
                ? "select d.email from DestinatarioEntity d where d.categoriaId is null order by d.id"
                : "select d.email from DestinatarioEntity d where d.categoriaId = :categoria order by d.id", String.class);
        if (categoriaId != null) consulta.setParameter("categoria", categoriaId);
        return new ListaDestinatarios(categoriaId, consulta.getResultList());
    }
    public void reemplazar(ListaDestinatarios lista) {
        var borrado = em.createQuery(lista.esGeneral()
                ? "delete from DestinatarioEntity d where d.categoriaId is null"
                : "delete from DestinatarioEntity d where d.categoriaId = :categoria");
        if (!lista.esGeneral()) borrado.setParameter("categoria", lista.categoriaId());
        borrado.executeUpdate();
        for (String email : lista.emails()) {
            var fila = new DestinatarioEntity();
            fila.categoriaId = lista.categoriaId(); fila.email = email;
            em.persist(fila);
        }
    }
}
