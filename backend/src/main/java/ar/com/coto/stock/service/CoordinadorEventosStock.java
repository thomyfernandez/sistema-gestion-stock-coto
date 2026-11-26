package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.observer.*;
import ar.com.coto.stock.domain.port.*;
import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import java.util.*;

/** Observa un grafo de inventario dentro de una transacción y persiste sus efectos:
 * estados, auditoría, sincronización digital, alertas outbox y pedidos de reposición. */
final class CoordinadorEventosStock {
    private final GestorInventario.Dependencias d;
    private final Map<Integer, Stock> grafo;
    private final Map<Integer, PoliticaReposicion> politicas;
    private final Map<Integer, AuditoriaStock> auditorias = new LinkedHashMap<>();
    private final Map<Integer, Integer> disponibilidades = new LinkedHashMap<>();
    private final Map<Integer, AlertaStockBajo.Aviso> avisos = new LinkedHashMap<>();
    private final Map<Integer, AlertaStockBajo> alertas = new LinkedHashMap<>();

    CoordinadorEventosStock(GestorInventario.Dependencias d, Map<Integer, Stock> grafo, Map<Integer, PoliticaReposicion> politicas) {
        this.d = Objects.requireNonNull(d);
        this.grafo = Objects.requireNonNull(grafo);
        this.politicas = Objects.requireNonNull(politicas);
        registrarObservadores();
    }

    static void encolarStock(GestorInventario.Dependencias d, String clave, int id, int disponible) {
        d.eventos().encolar(new EventoIntegracion(0, clave, EventoIntegracion.Tipo.STOCK_DIGITAL, id,
                null, disponible, null, null, null, d.reloj().instant()));
    }

    /** Disponibles finales de los stocks afectados, en el orden en que se observaron. */
    Map<Integer, Integer> disponibilidades() { return Collections.unmodifiableMap(disponibilidades); }

    /** Evalúa también un stock inicialmente bajo, aunque aún no haya recibido movimientos. */
    void evaluarAlertasVigentes() {
        alertas.forEach((id, alerta) -> {
            Stock stock = grafo.get(id);
            alerta.actualizar(stock);
            disponibilidades.put(id, stock.getDisponible());
        });
    }

    void guardarMovimiento(long movimientoId) {
        disponibilidades.keySet().forEach(id -> d.stocks().guardarEstado(grafo.get(id)));
        auditorias.values().forEach(a -> d.auditoria().guardar(a, movimientoId));
        String clave = "mov:" + movimientoId;
        disponibilidades.forEach((id, disponible) -> encolarStock(d, clave + ":stock:" + id, id, disponible));
        guardarAlertas(clave);
    }

    void guardarAlertas(String clave) {
        if (!avisos.isEmpty()) {
            var general = d.notificaciones().consultarConfiguracion();
            avisos.forEach((id, aviso) -> {
                var destinos = d.contactos().resolver(politicas.get(id), grafo.get(id).getProducto(), general);
                // Un evento por dirección: cada email se entrega y reintenta por separado.
                for (int i = 0; i < destinos.size(); i++) {
                    var destino = destinos.get(i);
                    d.eventos().encolar(new EventoIntegracion(0, clave + ":alerta:" + id + (destinos.size() == 1 ? "" : ":" + i),
                            EventoIntegracion.Tipo.ALERTA, id, null, null, destino.canal(),
                            destino.destino(), aviso.getTexto(), d.reloj().instant()));
                }
            });
        }
        disponibilidades.keySet().forEach(id -> {
            PoliticaReposicion p = politicas.get(id);
            if (p != null) d.politicas().marcarAlerta(id, grafo.get(id).getDisponible() < p.umbralAlerta());
        });
    }

    List<PedidoRegistrado> reponerAfectados() { return reponer(disponibilidades.keySet()); }

    List<PedidoRegistrado> reponerConPolitica() { return reponer(politicas.keySet()); }

    private void registrarObservadores() {
        AuditorMovimientos auditor = new AuditorMovimientos(a -> auditorias.merge(a.stockId(), a, (primero, ultimo) ->
                new AuditoriaStock(a.stockId(), ultimo.fecha(), primero.disponibleAnterior(), ultimo.disponibleActual(), ultimo.detalle())), d.reloj());
        SincronizadorCotoDigital sincronizador = new SincronizadorCotoDigital(disponibilidades::put);
        grafo.forEach((id, stock) -> {
            auditor.establecerLineaBase(stock);
            stock.agregarObservador(auditor);
            stock.agregarObservador(sincronizador);
            PoliticaReposicion politica = politicas.get(id);
            if (politica != null) {
                AlertaStockBajo alerta = new AlertaStockBajo(politica.umbralAlerta(), politica.destinoAlerta(),
                        a -> avisos.put(id, a.getUltimoAviso()));
                alerta.restaurarEstado(id, d.politicas().alertaActiva(id));
                alertas.put(id, alerta);
                stock.agregarObservador(alerta);
            }
        });
    }

    private List<PedidoRegistrado> reponer(Set<Integer> afectados) {
        List<PedidoRegistrado> generados = new ArrayList<>();
        for (int id : List.copyOf(afectados)) {
            PoliticaReposicion politica = politicas.get(id);
            if (politica == null) continue;
            Stock stock = grafo.get(id);
            if (stock == null) throw new EntidadNoEncontradaException("Stock", id);
            d.reposicion().planificar(stock, politica.crearEstrategia(), d.pedidos().cantidadPendiente(id))
                    .ifPresent(pedido -> {
                        PedidoRegistrado registrado = d.pedidos().guardar(pedido);
                        generados.add(registrado);
                        d.eventos().encolar(new EventoIntegracion(0, "pedido:" + registrado.id(), EventoIntegracion.Tipo.PEDIDO_ERP,
                                id, registrado.id(), null, null, null, null, d.reloj().instant()));
                    });
        }
        return List.copyOf(generados);
    }
}
