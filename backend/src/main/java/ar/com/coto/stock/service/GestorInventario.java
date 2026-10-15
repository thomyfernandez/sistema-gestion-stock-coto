package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.observer.*;
import ar.com.coto.stock.domain.port.*;
import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import ar.com.coto.stock.domain.valorizacion.Promocion;
import java.time.Clock;
import java.util.*;

/** Singleton GoF clásico: instancia privada, constructor privado y acceso estático explícito.
 * Spring registra ESTA instancia; no crea un segundo coordinador ni sustituye el patrón. */
public final class GestorInventario implements GestionInventario {
    private static final GestorInventario instancia = new GestorInventario();
    private volatile Dependencias dependencias;
    private GestorInventario() { }
    public static GestorInventario getInstancia() { return instancia; }

    public record Dependencias(UnidadDeTrabajo unidad, RepositorioStock stocks, RepositorioMovimientos movimientos,
                               RepositorioPedidos pedidos, RepositorioAuditoria auditoria, RepositorioPoliticas politicas,
                               RepositorioPromociones promociones, RepositorioIntegraciones eventos,
                               ServicioReposicion reposicion, ServicioNotificaciones notificaciones,
                               ServicioIntegraciones integraciones, Clock reloj) {
        public Dependencias {
            Objects.requireNonNull(unidad); Objects.requireNonNull(stocks); Objects.requireNonNull(movimientos);
            Objects.requireNonNull(pedidos); Objects.requireNonNull(auditoria); Objects.requireNonNull(politicas);
            Objects.requireNonNull(promociones); Objects.requireNonNull(eventos); Objects.requireNonNull(reposicion);
            Objects.requireNonNull(notificaciones); Objects.requireNonNull(integraciones); Objects.requireNonNull(reloj);
        }
    }
    public synchronized void configurar(Dependencias dependencias) {
        if (this.dependencias != null) throw new IllegalStateException("El Singleton ya está configurado");
        this.dependencias = Objects.requireNonNull(dependencias);
    }
    /** Libera conexiones al cerrar el contexto; la identidad del Singleton se mantiene. */
    public synchronized void cerrar() { dependencias = null; }
    private Dependencias d() {
        Dependencias actual = dependencias;
        if (actual == null) throw new IllegalStateException("Inicializá el contexto de aplicación antes de usar el gestor");
        return actual;
    }

    @Override public ResultadoMovimiento registrarMovimiento(MovimientoStock movimiento) {
        Objects.requireNonNull(movimiento);
        Dependencias d = d();
        return d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            return registrarDentroDeTransaccion(d, movimiento);
        });
    }

    private ResultadoMovimiento registrarDentroDeTransaccion(Dependencias d, MovimientoStock m) {
        Map<Integer, Stock> grafo = d.stocks.cargarInventario();
        Stock objetivo = exigirStock(grafo, m.getStockId());
        Map<Integer, PoliticaReposicion> politicas = d.politicas.listar();
        Observaciones observaciones = observar(d, grafo, politicas);
        if (objetivo instanceof StockSimple simple) simple.aplicarMovimiento(m);
        else switch (m.getTipo()) {
            case RESERVA_DIGITAL -> objetivo.reservar(m.getCantidad());
            case CANCELACION_RESERVA -> objetivo.liberarReserva(m.getCantidad());
            case DESPACHO_DIGITAL -> objetivo.despacharReserva(m.getCantidad());
            default -> throw new IllegalArgumentException("Recepción, ajustes y traslados requieren un producto simple");
        }
        long movimientoId = d.movimientos.guardar(m);
        observaciones.disponibilidades.keySet().forEach(id -> {
            if (grafo.get(id) instanceof StockSimple simple) d.stocks.guardarEstado(simple);
        });
        observaciones.auditorias.values().forEach(a -> d.auditoria.guardar(a, movimientoId));
        String clave = "mov:" + movimientoId;
        observaciones.disponibilidades.forEach((id, disponible) -> encolarStock(d, clave + ":stock:" + id, id, disponible));
        guardarAlertas(d, observaciones, politicas, grafo, clave);
        List<PedidoRegistrado> pedidos = reponer(d, grafo, politicas, observaciones.disponibilidades.keySet());
        d.unidad.despuesDeConfirmar(d.integraciones::procesarPendientes);
        return new ResultadoMovimiento(movimientoId, observaciones.disponibilidades, pedidos);
    }

    @Override public List<PedidoRegistrado> evaluarReposicion() {
        Dependencias d = d();
        return d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            Map<Integer, Stock> grafo = d.stocks.cargarInventario();
            Map<Integer, PoliticaReposicion> politicas = d.politicas.listar();
            Observaciones o = observar(d, grafo, politicas);
            // Evalúa también un stock inicialmente bajo, aunque aún no haya recibido movimientos.
            o.alertas.forEach((id, alerta) -> {
                alerta.actualizar(exigirStock(grafo, id));
                o.disponibilidades.put(id, grafo.get(id).getDisponible());
            });
            guardarAlertas(d, o, politicas, grafo, "eval:" + UUID.randomUUID());
            List<PedidoRegistrado> pedidos = reponer(d, grafo, politicas, politicas.keySet());
            d.unidad.despuesDeConfirmar(d.integraciones::procesarPendientes);
            return pedidos;
        });
    }

    private List<PedidoRegistrado> reponer(Dependencias d, Map<Integer, Stock> grafo,
                                          Map<Integer, PoliticaReposicion> politicas, Set<Integer> afectados) {
        List<PedidoRegistrado> generados = new ArrayList<>();
        for (int id : afectados) {
            PoliticaReposicion politica = politicas.get(id);
            if (politica == null) continue;
            d.reposicion.planificar(exigirStock(grafo, id), politica.crearEstrategia(), d.pedidos.cantidadPendiente(id))
                    .ifPresent(pedido -> {
                        PedidoRegistrado registrado = d.pedidos.guardar(pedido);
                        generados.add(registrado);
                        d.eventos.encolar(new EventoIntegracion(0, "pedido:" + registrado.id(), EventoIntegracion.Tipo.PEDIDO_ERP,
                                id, registrado.id(), null, null, null, null, d.reloj.instant()));
                    });
        }
        return List.copyOf(generados);
    }

    private Observaciones observar(Dependencias d, Map<Integer, Stock> grafo, Map<Integer, PoliticaReposicion> politicas) {
        Observaciones o = new Observaciones();
        AuditorMovimientos auditor = new AuditorMovimientos(a -> o.auditorias.merge(a.stockId(), a, (primero, ultimo) ->
                new AuditoriaStock(a.stockId(), ultimo.fecha(), primero.disponibleAnterior(), ultimo.disponibleActual(), ultimo.detalle())), d.reloj);
        SincronizadorCotoDigital sincronizador = new SincronizadorCotoDigital(o.disponibilidades::put);
        grafo.forEach((id, stock) -> {
            auditor.establecerLineaBase(stock);
            stock.agregarObservador(auditor);
            stock.agregarObservador(sincronizador);
            PoliticaReposicion politica = politicas.get(id);
            if (politica != null) {
                AlertaStockBajo alerta = new AlertaStockBajo(politica.umbralAlerta(), politica.destinoAlerta(),
                        a -> o.avisos.put(id, a.getUltimoAviso()));
                alerta.restaurarEstado(id, d.politicas.alertaActiva(id));
                o.alertas.put(id, alerta);
                stock.agregarObservador(alerta);
            }
        });
        return o;
    }

    private void guardarAlertas(Dependencias d, Observaciones o, Map<Integer, PoliticaReposicion> politicas,
                                Map<Integer, Stock> grafo, String clave) {
        o.avisos.forEach((id, aviso) -> d.eventos.encolar(new EventoIntegracion(0, clave + ":alerta:" + id,
                EventoIntegracion.Tipo.ALERTA, id, null, null, d.notificaciones.getCanal().name(),
                aviso.destino(), aviso.getTexto(), d.reloj.instant())));
        o.disponibilidades.keySet().forEach(id -> {
            PoliticaReposicion p = politicas.get(id);
            if (p != null) d.politicas.marcarAlerta(id, grafo.get(id).getDisponible() < p.umbralAlerta());
        });
    }
    private void encolarStock(Dependencias d, String clave, int id, int disponible) {
        d.eventos.encolar(new EventoIntegracion(0, clave, EventoIntegracion.Tipo.STOCK_DIGITAL, id,
                null, disponible, null, null, null, d.reloj.instant()));
    }

    @Override public Stock consultarStock(int id) {
        Dependencias d = d();
        return d.unidad.consultar(() -> exigirStock(d.stocks.cargarInventario(), id));
    }
    @Override public List<Stock> listarStock() {
        Dependencias d = d();
        return d.unidad.consultar(() -> List.copyOf(d.stocks.cargarInventario().values()));
    }
    @Override public Stock crearProducto(AltaProducto datos) {
        Objects.requireNonNull(datos);
        Dependencias d = d();
        return d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            Stock stock = d.stocks.crear(datos, false);
            encolarStock(d, "alta:" + stock.getProducto().getId(), stock.getProducto().getId(), 0);
            d.unidad.despuesDeConfirmar(d.integraciones::procesarPendientes);
            return stock;
        });
    }
    @Override public ComboStock crearCombo(AltaProducto datos, List<ComponenteCombo> componentes) {
        Objects.requireNonNull(datos);
        List<ComponenteCombo> lineas = List.copyOf(componentes);
        if (lineas.isEmpty()) throw new IllegalArgumentException("Un combo publicado debe tener componentes");
        Dependencias d = d();
        return d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            Map<Integer, Stock> grafo = d.stocks.cargarInventario();
            ComboStock combo = (ComboStock) d.stocks.crear(datos, true);
            lineas.forEach(l -> combo.agregar(exigirStock(grafo, l.stockId()), l.cantidad()));
            if (combo.getComponentes().stream().anyMatch(l -> l.getComponente() instanceof ComboStock c && c.getComponentes().isEmpty())) {
                throw new IllegalArgumentException("No se puede publicar un combo incompleto");
            }
            d.stocks.guardarComposicion(combo);
            encolarStock(d, "alta:" + combo.getProducto().getId(), combo.getProducto().getId(), combo.getDisponible());
            d.unidad.despuesDeConfirmar(d.integraciones::procesarPendientes);
            return combo;
        });
    }
    @Override public void configurarPolitica(PoliticaReposicion politica) {
        Dependencias d = d();
        d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            if (!(exigirStock(d.stocks.cargarInventario(), politica.stockId()) instanceof StockSimple)) {
                throw new IllegalArgumentException("La política requiere un producto simple");
            }
            d.politicas.guardar(politica);
            return null;
        });
    }
    @Override public PoliticaReposicion consultarPolitica(int id) {
        Dependencias d = d();
        return d.unidad.consultar(() -> {
            exigirStock(d.stocks.cargarInventario(), id);
            PoliticaReposicion p = d.politicas.listar().get(id);
            if (p == null) throw new EntidadNoEncontradaException("Política de reposición", id);
            return p;
        });
    }
    @Override public Promocion guardarPromocion(Promocion promocion) {
        Dependencias d = d();
        return d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            exigirStock(d.stocks.cargarInventario(), promocion.productoId());
            if (promocion.activa() && promocion.tipo() == Promocion.Tipo.DOS_POR_UNO && d.promociones.listar(promocion.productoId()).stream()
                    .anyMatch(p -> p.id() != promocion.id() && p.activa() && p.tipo() == Promocion.Tipo.DOS_POR_UNO)) {
                throw new IllegalArgumentException("Ya existe una promoción 2x1 activa");
            }
            return d.promociones.guardar(promocion);
        });
    }
    @Override public List<Promocion> listarPromociones(int id) {
        Dependencias d = d();
        return d.unidad.consultar(() -> { exigirStock(d.stocks.cargarInventario(), id); return d.promociones.listar(id); });
    }
    @Override public List<MovimientoRegistrado> listarMovimientos() {
        Dependencias d = d(); return d.unidad.consultar(d.movimientos::listar);
    }
    @Override public List<AuditoriaStock> listarAuditoria() {
        Dependencias d = d(); return d.unidad.consultar(d.auditoria::listar);
    }
    @Override public List<PedidoRegistrado> listarPedidos() {
        Dependencias d = d(); return d.unidad.consultar(d.pedidos::listar);
    }
    @Override public ResultadoMovimiento recibirPedido(long id, String usuario) {
        Dependencias d = d();
        return d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            PedidoRegistrado registrado = d.pedidos.obtener(id);
            registrado.pedido().marcarRecibido();
            d.pedidos.actualizar(registrado);
            return registrarDentroDeTransaccion(d, new MovimientoStock(registrado.pedido().getStockId(), TipoMovimiento.RECEPCION,
                    registrado.pedido().getCantidad(), d.reloj.instant(), "Recepción del pedido " + id, usuario));
        });
    }
    @Override public void cancelarPedido(long id) {
        Dependencias d = d();
        d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            PedidoRegistrado pedido = d.pedidos.obtener(id);
            pedido.pedido().cancelar();
            d.pedidos.actualizar(pedido);
            return null;
        });
    }
    private static Stock exigirStock(Map<Integer, Stock> grafo, int id) {
        Stock stock = grafo.get(id);
        if (stock == null) throw new EntidadNoEncontradaException("Stock", id);
        return stock;
    }
    private static final class Observaciones {
        final Map<Integer, AuditoriaStock> auditorias = new LinkedHashMap<>();
        final Map<Integer, Integer> disponibilidades = new LinkedHashMap<>();
        final Map<Integer, AlertaStockBajo.Aviso> avisos = new LinkedHashMap<>();
        final Map<Integer, AlertaStockBajo> alertas = new LinkedHashMap<>();
    }
}
