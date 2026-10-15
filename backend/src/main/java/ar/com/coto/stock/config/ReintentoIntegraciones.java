package ar.com.coto.stock.config;

import ar.com.coto.stock.service.ServicioIntegraciones;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "coto.integraciones.automaticas", havingValue = "true", matchIfMissing = true)
public class ReintentoIntegraciones {
    private final ServicioIntegraciones integraciones;
    public ReintentoIntegraciones(ServicioIntegraciones integraciones) { this.integraciones = integraciones; }
    @Scheduled(fixedDelayString = "${coto.integraciones.reintento-ms:5000}", initialDelayString = "${coto.integraciones.reintento-ms:5000}")
    public void reintentar() { integraciones.procesarPendientes(); }
}
