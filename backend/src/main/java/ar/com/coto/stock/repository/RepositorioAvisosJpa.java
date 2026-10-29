package ar.com.coto.stock.repository;
import ar.com.coto.stock.domain.model.ConfiguracionAvisos;
import ar.com.coto.stock.domain.port.RepositorioAvisos;
import ar.com.coto.stock.repository.entity.ConfiguracionAvisosEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
@Repository
public class RepositorioAvisosJpa implements RepositorioAvisos {
    @PersistenceContext private EntityManager em;
    private ConfiguracionAvisosEntity fila() {
        var fila = em.find(ConfiguracionAvisosEntity.class, 1);
        if (fila == null) throw new IllegalStateException("Falta inicializar la configuración de avisos");
        return fila;
    }
    public ConfiguracionAvisos obtener() { var f = fila(); return new ConfiguracionAvisos(f.canal, f.email, f.telefono); }
    public void guardar(ConfiguracionAvisos c) { var f = fila(); f.canal = c.canal(); f.email = c.email(); f.telefono = c.telefono(); }
}
