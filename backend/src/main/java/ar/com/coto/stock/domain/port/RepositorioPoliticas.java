package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import java.util.Map;

public interface RepositorioPoliticas {
    Map<Integer, PoliticaReposicion> listar();
    void guardar(PoliticaReposicion politica);
    boolean alertaActiva(int stockId);
    void marcarAlerta(int stockId, boolean activa);
}
