package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.port.UnidadDeTrabajo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import java.util.function.Supplier;

/** Adaptación transaccional explícita: el Singleton clásico no depende de un proxy @Transactional. */
@Component
public class UnidadDeTrabajoSpring implements UnidadDeTrabajo {
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;
    private final TransactionTemplate nueva;

    public UnidadDeTrabajoSpring(PlatformTransactionManager manager) {
        escritura = new TransactionTemplate(manager);
        lectura = new TransactionTemplate(manager);
        lectura.setReadOnly(true);
        lectura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        nueva = new TransactionTemplate(manager);
        nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override public <T> T ejecutar(Supplier<T> trabajo) { return escritura.execute(s -> trabajo.get()); }
    @Override public <T> T consultar(Supplier<T> trabajo) { return lectura.execute(s -> trabajo.get()); }
    @Override public <T> T ejecutarNueva(Supplier<T> trabajo) { return nueva.execute(s -> trabajo.get()); }
    @Override public void despuesDeConfirmar(Runnable accion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La entrega requiere una transacción activa");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { accion.run(); }
                catch (RuntimeException ex) {
                    // El commit ya ocurrió; el mensaje permanece pendiente para el reintento.
                    System.getLogger(UnidadDeTrabajoSpring.class.getName()).log(System.Logger.Level.WARNING,
                            "Entrega diferida pendiente: " + ex.getMessage());
                }
            }
        });
    }
}
