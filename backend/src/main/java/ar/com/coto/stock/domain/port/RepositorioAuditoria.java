package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.AuditoriaStock;
import ar.com.coto.stock.domain.model.Pagina;
import java.util.List;

public interface RepositorioAuditoria extends RegistroAuditoria {
    void guardar(AuditoriaStock auditoria, Long movimientoId);
    List<AuditoriaStock> listar();
    /** Del más reciente al más antiguo; stockId nulo no restringe. */
    Pagina<AuditoriaStock> buscar(Integer stockId, int pagina, int limite);
    @Override default void registrar(AuditoriaStock auditoria) { guardar(auditoria, null); }
}
