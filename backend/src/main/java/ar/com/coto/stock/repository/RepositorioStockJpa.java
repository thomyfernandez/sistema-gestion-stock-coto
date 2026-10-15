package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.RepositorioStock;
import ar.com.coto.stock.repository.entity.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class RepositorioStockJpa implements RepositorioStock {
    @PersistenceContext private EntityManager em;

    @Override public void bloquearInventario() {
        if (em.find(BloqueoEntity.class, 1, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new IllegalStateException("Falta inicializar el bloqueo del inventario");
        }
    }

    @Override public Map<Integer, Stock> cargarInventario() {
        Map<Integer, Stock> stocks = new LinkedHashMap<>();
        for (StockEntity fila : em.createQuery("select s from StockEntity s join fetch s.producto order by s.id", StockEntity.class).getResultList()) {
            Producto producto = new Producto(fila.id, fila.producto.nombre, fila.producto.descripcion, fila.producto.precio.doubleValue());
            Stock stock = switch (fila.tipo) {
                case "SIMPLE" -> new StockSimple(producto, fila.stockFisico, fila.stockExhibicion, fila.stockReservado);
                case "COMBO" -> new ComboStock(producto);
                default -> throw new IllegalStateException("Discriminador inválido: " + fila.tipo);
            };
            stocks.put(fila.id, stock);
        }
        // Una sola instancia por identidad, también cuando hay ramas compartidas.
        for (LineaComboEntity fila : em.createQuery("select l from LineaComboEntity l order by l.id.comboId, l.id.componenteId", LineaComboEntity.class).getResultList()) {
            ((ComboStock) stocks.get(fila.id.comboId)).agregar(stocks.get(fila.id.componenteId), fila.cantidadEnCombo);
        }
        return stocks;
    }

    @Override public void guardarEstado(StockSimple stock) {
        StockEntity fila = em.find(StockEntity.class, stock.getProducto().getId());
        if (fila == null) throw new EntidadNoEncontradaException("Stock", stock.getProducto().getId());
        fila.stockFisico = stock.getStockFisico();
        fila.stockExhibicion = stock.getStockExhibicion();
        fila.stockReservado = stock.getStockReservado();
    }

    @Override public Stock crear(AltaProducto datos, boolean combo) {
        ProductoEntity producto = new ProductoEntity();
        producto.nombre = datos.nombre(); producto.descripcion = datos.descripcion();
        producto.precio = BigDecimal.valueOf(datos.precio());
        em.persist(producto);
        StockEntity fila = new StockEntity();
        fila.producto = producto; fila.id = producto.id; fila.tipo = combo ? "COMBO" : "SIMPLE";
        if (!combo) { fila.stockFisico = 0; fila.stockExhibicion = 0; fila.stockReservado = 0; }
        em.persist(fila);
        Producto dominio = new Producto(producto.id, datos.nombre(), datos.descripcion(), datos.precio());
        return combo ? new ComboStock(dominio) : new StockSimple(dominio, 0, 0, 0);
    }

    @Override public void guardarComposicion(ComboStock combo) {
        int id = combo.getProducto().getId();
        em.createQuery("delete from LineaComboEntity l where l.id.comboId = :id").setParameter("id", id).executeUpdate();
        for (LineaCombo linea : combo.getComponentes()) {
            LineaComboEntity fila = new LineaComboEntity();
            fila.id = new LineaComboId(id, linea.getComponente().getProducto().getId());
            fila.cantidadEnCombo = linea.getCantidadEnCombo();
            em.persist(fila);
        }
    }
}
