package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.*;
import ar.com.coto.stock.domain.valorizacion.Promocion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ServicioValorizacionTest {

    @Test
    void ignoraPromocionMedioPagoCuandoElParametroEsNulo() {
        var producto = new Producto(1, "Arroz COTO", "1 kg", new BigDecimal("1000"));
        var stock = new StockSimple(producto, 10, 0, 0);
        RepositorioStock stocks = new RepositorioStock() {
            @Override public void bloquearInventario() { }
            @Override public Map<Integer, Stock> cargarInventario() { return Map.of(1, stock); }
            @Override public Optional<Stock.Tipo> tipo(int id) { return id == 1 ? Optional.of(Stock.Tipo.SIMPLE) : Optional.empty(); }
            @Override public Optional<Producto> producto(int id) { return id == 1 ? Optional.of(producto) : Optional.empty(); }
            @Override public void guardarEstado(Stock s) { }
            @Override public Stock crear(AltaProducto p, boolean combo) { throw new UnsupportedOperationException(); }
            @Override public void guardarCategoria(int productoId, Integer categoriaId) { }
            @Override public void guardarComposicion(ComboStock combo) { }
            @Override public Optional<ImagenProducto> consultarImagen(int productoId) { return Optional.empty(); }
        };
        var promocionesDelProducto = List.of(
                new Promocion(1L, 1, Promocion.Tipo.MEDIO_PAGO, new BigDecimal("15"), 1, true, "VISA"),
                new Promocion(2L, 1, Promocion.Tipo.DESCUENTO, new BigDecimal("10"), 2, true, null));
        RepositorioPromociones promos = new RepositorioPromociones() {
            @Override public List<Promocion> listar(int productoId) { return promocionesDelProducto; }
            @Override public Map<Integer, List<Promocion>> listarPorProducto() { return Map.of(1, promocionesDelProducto); }
            @Override public Promocion guardar(Promocion promocion) { return promocion; }
            @Override public void eliminar(long promocionId) { }
        };
        UnidadDeTrabajo unidad = new UnidadDeTrabajo() {
            @Override public <T> T ejecutar(Supplier<T> accion) { return accion.get(); }
            @Override public <T> T consultar(Supplier<T> accion) { return accion.get(); }
            @Override public <T> T ejecutarNueva(Supplier<T> accion) { return accion.get(); }
            @Override public void despuesDeConfirmar(Runnable accion) { }
        };

        var servicio = new ServicioValorizacion(stocks, promos, unidad);
        var calculado = servicio.valorizar(1, 2, null);

        assertEquals(new BigDecimal("2000.00"), calculado.precioLista());
        assertEquals(new BigDecimal("1800.00"), calculado.precioFinal());
        assertEquals(List.of(2L), calculado.promocionesAplicadas());
    }
}
