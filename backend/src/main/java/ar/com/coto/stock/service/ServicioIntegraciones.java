package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.*;
import java.util.List;

/** Despachador recuperable de outbox, FIFO y con exclusión entre instancias de la aplicación. */
public final class ServicioIntegraciones implements GestionIntegraciones {
    private final UnidadDeTrabajo unidad;
    private final RepositorioIntegraciones eventos;
    private final RepositorioPedidos pedidos;
    private final RepositorioStock stocks;
    private final IntegracionERP erp;
    private final PublicadorDisponibilidad digital;
    private final ServicioNotificaciones notificaciones;
    private final MonitorSistemasExternos monitor;
    public ServicioIntegraciones(UnidadDeTrabajo unidad, RepositorioIntegraciones eventos, RepositorioPedidos pedidos, RepositorioStock stocks,
                                  IntegracionERP erp, PublicadorDisponibilidad digital, ServicioNotificaciones notificaciones,
                                  MonitorSistemasExternos monitor) {
        this.unidad = unidad; this.eventos = eventos; this.pedidos = pedidos;
        this.stocks = stocks;
        this.erp = erp; this.digital = digital; this.notificaciones = notificaciones;
        this.monitor = monitor;
    }
    @Override public Estado consultarEstado() {
        return unidad.consultar(() -> new Estado(eventos.listar(), monitor.consultar()));
    }
    @Override public void simularDisponibilidad(GestionIntegraciones.Sistema sistema, boolean disponible) {
        monitor.establecerDisponibilidad(MonitorSistemasExternos.Sistema.valueOf(java.util.Objects.requireNonNull(sistema).name()), disponible);
    }
    public int procesarPendientes() {
        return unidad.ejecutarNueva(() -> {
            eventos.bloquearEnvios();
            stocks.bloquearInventario();
            List<Long> pendientes = eventos.pendientes(100);
            int enviados = 0;
            for (long id : pendientes) {
                EventoIntegracion e = eventos.obtenerPendiente(id);
                if (e == null) continue;
                try {
                    switch (e.tipo()) {
                        case STOCK_DIGITAL -> digital.sincronizar(e.stockId(), e.disponible());
                        case ALERTA -> notificaciones.enviar(ServicioNotificaciones.Canal.valueOf(e.canal()), e.destino(), e.texto());
                        case PEDIDO_ERP -> {
                            // Mismo orden de bloqueo que otros despachadores. Los gestores sólo
                            // toman inventario; no esperan por envíos hasta después del commit.
                            PedidoRegistrado pedido = pedidos.obtener(e.pedidoId());
                            if (pedido.pedido().getEstado() == EstadoPedido.PENDIENTE) {
                                erp.enviarPedido(pedido);
                                pedido.pedido().marcarEnviado();
                                pedidos.actualizar(pedido);
                            }
                        }
                    }
                    eventos.registrarExito(id);
                    enviados++;
                } catch (RuntimeException ex) {
                    eventos.registrarFallo(id, ex.getMessage());
                    // Detener el lote preserva orden: un stock antiguo no puede sobrescribir uno nuevo.
                    break;
                }
            }
            return enviados;
        });
    }
}
