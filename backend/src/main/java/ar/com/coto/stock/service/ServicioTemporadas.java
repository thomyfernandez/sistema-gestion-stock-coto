package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.EntidadNoEncontradaException;
import ar.com.coto.stock.domain.model.Stock;
import ar.com.coto.stock.domain.port.RepositorioCategorias;
import ar.com.coto.stock.domain.port.RepositorioStock;
import ar.com.coto.stock.domain.port.RepositorioTemporadas;
import ar.com.coto.stock.domain.port.UnidadDeTrabajo;
import ar.com.coto.stock.domain.reposicion.CalendarioTemporadas;
import ar.com.coto.stock.domain.reposicion.Temporada;
import ar.com.coto.stock.domain.reposicion.TemporadaSuperpuestaException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Alta, edición y baja de temporadas. Comparte el bloqueo de inventario con la reposición automática,
 * así una evaluación nunca ve un calendario a medio cambiar. La fecha sale del reloj de la sucursal. */
public final class ServicioTemporadas implements GestionTemporadas {
    private static final String COMBO = "Las temporadas se cargan sobre artículos simples; un combo se repone por sus componentes";
    private final RepositorioTemporadas temporadas;
    private final RepositorioStock stocks;
    private final RepositorioCategorias categorias;
    private final UnidadDeTrabajo unidad;
    private final Clock reloj;

    public ServicioTemporadas(RepositorioTemporadas temporadas, RepositorioStock stocks, RepositorioCategorias categorias,
                              UnidadDeTrabajo unidad, Clock reloj) {
        this.temporadas = Objects.requireNonNull(temporadas); this.stocks = Objects.requireNonNull(stocks);
        this.categorias = Objects.requireNonNull(categorias); this.unidad = Objects.requireNonNull(unidad);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override public List<Consulta> listar() {
        return unidad.consultar(() -> {
            LocalDate hoy = LocalDate.now(reloj);
            return temporadas.listar().stream().map(t -> new Consulta(t, t.incluye(hoy))).toList();
        });
    }
    @Override public Consulta crear(Temporada temporada) {
        Objects.requireNonNull(temporada);
        if (temporada.id() != 0) throw new IllegalArgumentException("Una temporada nueva no lleva id");
        return guardar(temporada);
    }
    @Override public Consulta editar(long id, Temporada temporada) {
        Objects.requireNonNull(temporada);
        return unidad.ejecutar(() -> {
            stocks.bloquearInventario();
            exigir(id);
            return guardarValidada(temporada.conId(id));
        });
    }
    @Override public void eliminar(long id) {
        unidad.ejecutar(() -> {
            stocks.bloquearInventario();
            exigir(id);
            temporadas.eliminar(id);
            return null;
        });
    }
    @Override public Vigencia factorVigente(int stockId) {
        return unidad.consultar(() -> {
            Stock stock = stocks.cargarInventario().get(stockId);
            if (stock == null) throw new EntidadNoEncontradaException("Stock", stockId);
            if (stock.getTipo() != Stock.Tipo.SIMPLE) throw new IllegalArgumentException(COMBO);
            Integer categoriaId = stock.getProducto().getCategoriaId();
            LocalDate hoy = LocalDate.now(reloj);
            var calendario = CalendarioTemporadas.para(stockId, categoriaId, temporadas.listarPara(stockId, categoriaId));
            return new Vigencia(stockId, hoy, calendario.factorPara(hoy));
        });
    }

    private Consulta guardar(Temporada temporada) {
        return unidad.ejecutar(() -> {
            stocks.bloquearInventario();
            return guardarValidada(temporada);
        });
    }
    private Consulta guardarValidada(Temporada temporada) {
        Map<Integer, Stock> inventario = temporada.esDeArticulos() ? stocks.cargarInventario() : Map.of();
        for (int stockId : temporada.stockIds()) {
            Stock stock = inventario.get(stockId);
            if (stock == null) throw new EntidadNoEncontradaException("Stock", stockId);
            if (stock.getTipo() != Stock.Tipo.SIMPLE) throw new IllegalArgumentException(COMBO);
        }
        if (!temporada.esDeArticulos()) {
            categorias.obtener(temporada.categoriaId()).orElseThrow(() -> new EntidadNoEncontradaException("Categoría", temporada.categoriaId()));
        }
        for (Temporada otra : temporadas.listar()) {
            if (otra.id() == temporada.id() || !otra.comparteAlcance(temporada) || !otra.superponeCon(temporada)) continue;
            throw new TemporadaSuperpuestaException(otra, temporada.esDeArticulos()
                    ? "en " + nombres(otra.articulosEnComun(temporada), inventario) : "en la misma categoría");
        }
        Temporada guardada = temporadas.guardar(temporada);
        return new Consulta(guardada, guardada.incluye(LocalDate.now(reloj)));
    }
    /** "Pan dulce", "Pan dulce y Budín" o "Pan dulce, Budín y 2 artículos más". */
    private static String nombres(List<Integer> ids, Map<Integer, Stock> inventario) {
        List<String> n = ids.stream().map(id -> inventario.containsKey(id) ? inventario.get(id).getProducto().getNombre() : "#" + id).toList();
        if (n.size() == 1) return n.getFirst();
        if (n.size() == 2) return n.get(0) + " y " + n.get(1);
        return n.get(0) + ", " + n.get(1) + " y " + (n.size() - 2) + (n.size() == 3 ? " artículo más" : " artículos más");
    }
    private Temporada exigir(long id) {
        return temporadas.obtener(id).orElseThrow(() -> new EntidadNoEncontradaException("Temporada", id));
    }
}
