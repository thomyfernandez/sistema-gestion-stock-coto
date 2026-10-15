package ar.com.coto.stock.domain.port;

import ar.com.coto.stock.domain.model.AuditoriaStock;

@FunctionalInterface
public interface RegistroAuditoria {
    void registrar(AuditoriaStock auditoria);
}
