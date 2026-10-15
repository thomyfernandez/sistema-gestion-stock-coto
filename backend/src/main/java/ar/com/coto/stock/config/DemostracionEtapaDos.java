package ar.com.coto.stock.config;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.service.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class DemostracionEtapaDos {
    @Bean
    @ConditionalOnProperty(name = "coto.demo.ejecutar", havingValue = "true")
    CommandLineRunner demo(GestionInventario gestor, ServicioValorizacion precios, Clock reloj) {
        return args -> {
            var resultado = gestor.registrarMovimiento(new MovimientoStock(6, TipoMovimiento.RESERVA_DIGITAL, 2,
                    reloj.instant(), "Demostración Etapa 2", "digital.demo"));
            System.out.println("Movimiento confirmado: " + resultado.id());
            System.out.println("Disponibilidad del combo: " + gestor.consultarStock(6).getDisponible());
            gestor.evaluarReposicion();
            System.out.println("Pedidos persistidos: " + gestor.listarPedidos().size());
            System.out.println("Precio de 3 arroces con 2x1, 10% y débito: " + precios.valorizar(1, 3, "DEBITO").precioFinal());
        };
    }
}
