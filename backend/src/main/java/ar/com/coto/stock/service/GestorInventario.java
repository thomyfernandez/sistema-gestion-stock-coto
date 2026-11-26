package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
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
                               ServicioReposicion reposicion, GestionNotificaciones notificaciones,
                               GestionIntegraciones integraciones, ResolutorDestinosAviso destinos, GestionCategorias categorias, Clock reloj) {
        public Dependencias {
            Objects.requireNonNull(unidad); Objects.requireNonNull(stocks); Objects.requireNonNull(movimientos);
            Objects.requireNonNull(pedidos); Objects.requireNonNull(auditoria); Objects.requireNonNull(politicas);
            Objects.requireNonNull(promociones); Objects.requireNonNull(eventos); Objects.requireNonNull(reposicion);
            Objects.requireNonNull(notificaciones); Objects.requireNonNull(integraciones); Objects.requireNonNull(reloj); Objects.requireNonNull(destinos); Objects.requireNonNull(categorias);
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
        var coordinador = new CoordinadorEventosStock(d, grafo, d.politicas.listar());
        objetivo.aplicarMovimiento(m);
        long movimientoId = d.movimientos.guardar(m);
        coordinador.guardarMovimiento(movimientoId);
        List<PedidoRegistrado> pedidos = coordinador.reponerAfectados();
        d.unidad.despuesDeConfirmar(d.integraciones::procesarPendientes);
        return new ResultadoMovimiento(movimientoId, coordinador.disponibilidades(), pedidos);
    }

    @Override public List<PedidoRegistrado> evaluarReposicion() {
        Dependencias d = d();
        return d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            Map<Integer, Stock> grafo = d.stocks.cargarInventario();
            var coordinador = new CoordinadorEventosStock(d, grafo, d.politicas.listar());
            coordinador.evaluarAlertasVigentes();
            coordinador.guardarAlertasDeEvaluacion();
            List<PedidoRegistrado> pedidos = coordinador.reponerConPolitica();
            d.unidad.despuesDeConfirmar(d.integraciones::procesarPendientes);
            return pedidos;
        });
    }

    @Override public Stock consultarStock(int id) {
        Dependencias d = d();
        return d.unidad.consultar(() -> exigirStock(d.stocks.cargarInventario(), id));
    }
    @Override public List<Stock> listarStock() {
        Dependencias d = d();
        return d.unidad.consultar(() -> List.copyOf(d.stocks.cargarInventario().values()));
    }
    @Override public ImagenProducto consultarImagen(int id) {
        Dependencias d = d();
        return d.unidad.consultar(() -> d.stocks.consultarImagen(id).orElseThrow(() -> new EntidadNoEncontradaException("Imagen de producto", id)));
    }
    @Override public Stock crearProducto(AltaProducto datos) {
        Objects.requireNonNull(datos);
        Dependencias d = d();
        return d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            if (datos.categoriaId() != null) d.categorias.consultar(datos.categoriaId());
            Stock stock = d.stocks.crear(datos, false);
            CoordinadorEventosStock.encolarAlta(d, stock);
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
            if (datos.categoriaId() != null) d.categorias.consultar(datos.categoriaId());
            ComboStock combo = (ComboStock) d.stocks.crear(datos, true);
            lineas.forEach(l -> combo.agregar(exigirStock(grafo, l.stockId()), l.cantidad()));
            if (!combo.estaCompleto()) throw new IllegalArgumentException("No se puede publicar un combo incompleto");
            d.stocks.guardarComposicion(combo);
            CoordinadorEventosStock.encolarAlta(d, combo);
            d.unidad.despuesDeConfirmar(d.integraciones::procesarPendientes);
            return combo;
        });
    }
    @Override public void asignarCategoria(int productoId, Integer categoriaId) {
        Dependencias d = d();
        d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            exigirTipo(d, productoId);
            if (categoriaId != null) d.categorias.consultar(categoriaId);
            d.stocks.guardarCategoria(productoId, categoriaId);
            return null;
        });
    }
    @Override public void configurarPolitica(PoliticaReposicion politica) {
        Dependencias d = d();
        d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            if (exigirTipo(d, politica.stockId()) != Stock.Tipo.SIMPLE) {
                throw new IllegalArgumentException("La política requiere un producto simple");
            }
            Producto producto = d.stocks.producto(politica.stockId()).orElseThrow();
            d.destinos.respaldo(politica, producto, d.notificaciones.consultarConfiguracion());
            d.politicas.guardar(politica);
            return null;
        });
    }
    @Override public PoliticaReposicion consultarPolitica(int id) {
        Dependencias d = d();
        return d.unidad.consultar(() -> {
            exigirTipo(d, id);
            PoliticaReposicion p = d.politicas.listar().get(id);
            if (p == null) throw new EntidadNoEncontradaException("Política de reposición", id);
            return p;
        });
    }
    @Override public Promocion guardarPromocion(Promocion promocion) {
        Dependencias d = d();
        return d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            exigirTipo(d, promocion.productoId());
            var resultantes = new ArrayList<>(d.promociones.listar(promocion.productoId()));
            resultantes.removeIf(p -> p.id() == promocion.id());
            resultantes.add(promocion);
            Promocion.exigirUnSoloDosPorUnoActivo(resultantes);
            return d.promociones.guardar(promocion);
        });
    }
    @Override public void eliminarPromocion(int productoId, long promocionId) {
        Dependencias d = d();
        d.unidad.ejecutar(() -> {
            d.stocks.bloquearInventario();
            exigirTipo(d, productoId);
            consultarPromocion(productoId, promocionId);
            d.promociones.eliminar(promocionId);
            return null;
        });
    }
    @Override public List<Promocion> listarPromociones(int id) {
        Dependencias d = d();
        return d.unidad.consultar(() -> { exigirTipo(d, id); return d.promociones.listar(id); });
    }
    @Override public List<MovimientoRegistrado> listarMovimientos() {
        Dependencias d = d(); return d.unidad.consultar(d.movimientos::listar);
    }
    @Override public MovimientoRegistrado consultarMovimiento(long id) {
        Dependencias d = d();
        return d.unidad.consultar(() -> d.movimientos.obtener(id).orElseThrow(() -> new EntidadNoEncontradaException("Movimiento", id)));
    }
    @Override public PedidoRegistrado consultarPedido(long id) {
        Dependencias d = d(); return d.unidad.consultar(() -> d.pedidos.obtener(id));
    }
    @Override public List<AuditoriaStock> listarAuditoria() {
        Dependencias d = d(); return d.unidad.consultar(d.auditoria::listar);
    }
    @Override public Pagina<MovimientoRegistrado> buscarMovimientos(Integer stockId, Set<TipoMovimiento> tipos, int pagina, int limite) {
        Dependencias d = d(); return d.unidad.consultar(() -> d.movimientos.buscar(stockId, tipos, pagina, limite));
    }
    @Override public Pagina<AuditoriaStock> buscarAuditoria(Integer stockId, int pagina, int limite) {
        Dependencias d = d(); return d.unidad.consultar(() -> d.auditoria.buscar(stockId, pagina, limite));
    }
    @Override public Map<Integer, PoliticaReposicion> listarPoliticas() {
        Dependencias d = d(); return d.unidad.consultar(() -> Collections.unmodifiableMap(d.politicas.listar()));
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
    private static Stock.Tipo exigirTipo(Dependencias d, int id) {
        return d.stocks.tipo(id).orElseThrow(() -> new EntidadNoEncontradaException("Stock", id));
    }
    private static Stock exigirStock(Map<Integer, Stock> grafo, int id) {
        Stock stock = grafo.get(id);
        if (stock == null) throw new EntidadNoEncontradaException("Stock", id);
        return stock;
    }
}
