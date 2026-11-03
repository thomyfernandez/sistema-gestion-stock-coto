package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.DestinatarioAviso;
import ar.com.coto.stock.domain.model.EntidadNoEncontradaException;
import ar.com.coto.stock.domain.port.RepositorioCategorias;
import ar.com.coto.stock.domain.port.RepositorioDestinatarios;
import ar.com.coto.stock.domain.port.RepositorioStock;
import ar.com.coto.stock.domain.port.UnidadDeTrabajo;

import java.util.*;

/**
 * Servicio de aplicación para la gestión y resolución jerárquica de destinatarios de avisos.
 * Jerarquía: correos específicos de la categoría si existen; en su defecto, lista general de la sucursal.
 */
public final class ServicioDestinatarios implements GestionDestinatarios {
    private final RepositorioDestinatarios destinatarios;
    private final RepositorioStock stocks;
    private final RepositorioCategorias categorias;
    private final UnidadDeTrabajo unidad;

    public ServicioDestinatarios(RepositorioDestinatarios destinatarios, RepositorioStock stocks, UnidadDeTrabajo unidad) {
        this(destinatarios, stocks, null, unidad);
    }

    public ServicioDestinatarios(RepositorioDestinatarios destinatarios, RepositorioStock stocks,
                                 RepositorioCategorias categorias, UnidadDeTrabajo unidad) {
        this.destinatarios = Objects.requireNonNull(destinatarios);
        this.stocks = Objects.requireNonNull(stocks);
        this.categorias = categorias;
        this.unidad = Objects.requireNonNull(unidad);
    }

    @Override
    public List<String> listarGenerales() {
        return unidad.consultar(() -> destinatarios.listarGenerales()
                .stream()
                .map(DestinatarioAviso::email)
                .toList());
    }

    @Override
    public Map<Integer, List<String>> listarPorCategoria() {
        return unidad.consultar(() -> {
            Map<Integer, List<String>> mapa = new LinkedHashMap<>();
            for (DestinatarioAviso d : destinatarios.listarTodos()) {
                if (d.categoriaId() != null) {
                    mapa.computeIfAbsent(d.categoriaId(), k -> new ArrayList<>()).add(d.email());
                }
            }
            return mapa;
        });
    }

    @Override
    public List<String> resolverDestinatarios(int productoId) {
        return unidad.consultar(() -> {
            var grafo = stocks.cargarInventario();
            var stock = grafo.get(productoId);
            Integer categoriaId = stock != null ? stock.getProducto().getCategoriaId() : null;
            if (categoriaId != null) {
                List<String> deCategoria = destinatarios.listarPorCategoria(categoriaId)
                        .stream()
                        .map(DestinatarioAviso::email)
                        .toList();
                if (!deCategoria.isEmpty()) {
                    return deCategoria;
                }
            }
            return destinatarios.listarGenerales()
                    .stream()
                    .map(DestinatarioAviso::email)
                    .toList();
        });
    }

    @Override
    public void guardarGenerales(List<String> emails) {
        unidad.ejecutar(() -> {
            if (emails != null) {
                for (String e : emails) {
                    new DestinatarioAviso(null, e);
                }
            }
            destinatarios.guardarGenerales(emails);
            return null;
        });
    }

    @Override
    public void guardarPorCategoria(int categoriaId, List<String> emails) {
        unidad.ejecutar(() -> {
            if (categoriaId <= 0) {
                throw new IllegalArgumentException("El categoriaId debe ser positivo");
            }
            if (categorias != null) {
                categorias.obtener(categoriaId)
                        .orElseThrow(() -> new EntidadNoEncontradaException("Categoría", categoriaId));
            }
            if (emails != null) {
                for (String e : emails) {
                    new DestinatarioAviso(categoriaId, e);
                }
            }
            destinatarios.guardarPorCategoria(categoriaId, emails);
            return null;
        });
    }
}
