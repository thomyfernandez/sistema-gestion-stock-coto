package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.AuditoriaStock;
import java.util.List;

public interface RepositorioAuditoria extends RegistroAuditoria {
    void guardar(AuditoriaStock auditoria, Long movimientoId);
    List<AuditoriaStock> listar();
    @Override default void registrar(AuditoriaStock auditoria) { guardar(auditoria, null); }
}
