package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.port.RepositorioPoliticas;
import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import ar.com.coto.stock.repository.entity.PoliticaEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class RepositorioPoliticasJpa implements RepositorioPoliticas {
    @PersistenceContext private EntityManager em;
    @Override public Map<Integer, PoliticaReposicion> listar() {
        Map<Integer, PoliticaReposicion> politicas = new LinkedHashMap<>();
        em.createQuery("select p from PoliticaEntity p order by p.stockId", PoliticaEntity.class).getResultList().forEach(p ->
                politicas.put(p.stockId, new PoliticaReposicion(p.stockId, PoliticaReposicion.Tipo.valueOf(p.tipo),
                        p.puntoPedido, p.stockObjetivo, p.demandaDiaria.doubleValue(), p.diasCobertura,
                        p.factorEstacional.doubleValue(), p.umbralAlerta, p.destinoAlerta)));
        return politicas;
    }
    @Override public void guardar(PoliticaReposicion p) {
        PoliticaEntity fila = em.find(PoliticaEntity.class, p.stockId());
        boolean nueva = fila == null;
        if (nueva) { fila = new PoliticaEntity(); fila.stockId = p.stockId(); }
        fila.tipo = p.tipo().name(); fila.puntoPedido = p.puntoPedido(); fila.stockObjetivo = p.stockObjetivo();
        fila.demandaDiaria = BigDecimal.valueOf(p.demandaDiaria()); fila.diasCobertura = p.diasCobertura();
        fila.factorEstacional = BigDecimal.valueOf(p.factorEstacional()); fila.umbralAlerta = p.umbralAlerta();
        fila.destinoAlerta = p.destinoAlerta();
        if (nueva) em.persist(fila);
    }
    @Override public boolean alertaActiva(int id) {
        PoliticaEntity fila = em.find(PoliticaEntity.class, id);
        return fila != null && fila.alertaActiva;
    }
    @Override public void marcarAlerta(int id, boolean activa) {
        PoliticaEntity fila = em.find(PoliticaEntity.class, id);
        if (fila != null) fila.alertaActiva = activa;
    }
}
