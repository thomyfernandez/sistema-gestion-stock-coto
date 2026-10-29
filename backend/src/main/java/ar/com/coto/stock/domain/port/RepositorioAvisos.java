package ar.com.coto.stock.domain.port;
import ar.com.coto.stock.domain.model.ConfiguracionAvisos;
public interface RepositorioAvisos {
    ConfiguracionAvisos obtener();
    void guardar(ConfiguracionAvisos configuracion);
}
