package ar.com.coto.stock.config;

import ar.com.coto.stock.adapter.*;
import ar.com.coto.stock.domain.notificacion.*;
import ar.com.coto.stock.domain.port.*;
import ar.com.coto.stock.domain.reposicion.ReposicionPuntoDePedido;
import ar.com.coto.stock.service.*;
import org.springframework.context.annotation.*;
import java.time.Clock;
import java.util.Map;

/** Único punto de composición. Servicios y dominio reciben contratos, no repositorios concretos. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionAplicacion {
    @Bean Clock reloj() { return Clock.systemUTC(); }
    @Bean ApiProveedorExterno proveedorExterno() { return new ApiProveedorExterno(); }
    @Bean SistemaERPExterno erpExterno() { return new SistemaERPExterno(); }
    @Bean ApiCotoDigitalExterna cotoDigitalExterno() { return new ApiCotoDigitalExterna(); }
    @Bean IntegracionERP erp(SistemaERPExterno api) { return new AdaptadorERP(api); }
    @Bean PublicadorDisponibilidad digital(ApiCotoDigitalExterna api) { return new AdaptadorCotoDigital(api); }
    @Bean MonitorSistemasExternos monitor(SistemaERPExterno erp, ApiProveedorExterno mensajes, ApiCotoDigitalExterna digital) {
        return new MonitorSimulado(erp, mensajes, digital);
    }
    @Bean ServicioNotificaciones notificaciones(ApiProveedorExterno api, RepositorioAvisos avisos, UnidadDeTrabajo unidad) {
        return new ServicioNotificaciones(Map.of(
                ServicioNotificaciones.Canal.EMAIL, new EmailNotificador(new AdaptadorProveedor(api, "EMAIL")),
                ServicioNotificaciones.Canal.SMS, new SmsNotificador(new AdaptadorProveedorSMS(api)),
                ServicioNotificaciones.Canal.WHATSAPP, new WhatsAppNotificador(new AdaptadorProveedor(api, "WHATSAPP"))), avisos, unidad);
    }
    @Bean ServicioContactos contactos(RepositorioContactos contactos, RepositorioPoliticas politicas, RepositorioStock stocks, UnidadDeTrabajo unidad, RepositorioCategorias categorias) {
        return new ServicioContactos(contactos, politicas, stocks, unidad, categorias);
    }
    @Bean ServicioCategorias categorias(RepositorioCategorias categorias, RepositorioContactos contactos, RepositorioStock stocks, UnidadDeTrabajo unidad) {
        return new ServicioCategorias(categorias, contactos, stocks, unidad);
    }
    @Bean ServicioReposicion reposicion(Clock reloj) { return new ServicioReposicion(new ReposicionPuntoDePedido(20, 100), reloj); }
    @Bean ServicioValorizacion valorizacion(RepositorioStock stock, RepositorioPromociones promociones, UnidadDeTrabajo unidad) {
        return new ServicioValorizacion(stock, promociones, unidad);
    }
    @Bean ServicioIntegraciones integraciones(UnidadDeTrabajo unidad, RepositorioIntegraciones eventos, RepositorioPedidos pedidos,
                                            RepositorioStock stocks, IntegracionERP erp, PublicadorDisponibilidad digital,
                                            ServicioNotificaciones notificaciones, MonitorSistemasExternos monitor) {
        return new ServicioIntegraciones(unidad, eventos, pedidos, stocks, erp, digital, notificaciones, monitor);
    }
    @Bean(destroyMethod = "cerrar") GestorInventario gestor(UnidadDeTrabajo unidad, RepositorioStock stocks,
            RepositorioMovimientos movimientos, RepositorioPedidos pedidos, RepositorioAuditoria auditoria,
            RepositorioPoliticas politicas, RepositorioPromociones promociones, RepositorioIntegraciones eventos,
            ServicioReposicion reposicion, ServicioNotificaciones notificaciones, ServicioIntegraciones integraciones, ServicioContactos contactos, ServicioCategorias categorias, Clock reloj) {
        GestorInventario gestor = GestorInventario.getInstancia();
        gestor.configurar(new GestorInventario.Dependencias(unidad, stocks, movimientos, pedidos, auditoria,
                politicas, promociones, eventos, reposicion, notificaciones, integraciones, contactos, categorias, reloj));
        return gestor;
    }
}
