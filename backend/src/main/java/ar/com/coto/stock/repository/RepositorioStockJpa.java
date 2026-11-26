package ar.com.coto.stock.repository;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.RepositorioStock;
import ar.com.coto.stock.repository.entity.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class RepositorioStockJpa implements RepositorioStock {
    @PersistenceContext private EntityManager em;

    @Override public void bloquearInventario() {
        if (em.find(BloqueoEntity.class, 1, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new IllegalStateException("Falta inicializar el bloqueo del inventario");
        }
    }

    @Override public Optional<Stock.Tipo> tipo(int id) {
        // Proyección para no dejar un proxy diferido del producto en el contexto (los campos públicos no lo inicializan).
        return em.createQuery("select s.tipo from StockEntity s where s.id = :id", String.class)
                .setParameter("id", id).getResultStream().findFirst().map(RepositorioStockJpa::tipo);
    }

    @Override public Optional<Producto> producto(int id) {
        // Proyección: las entidades exponen campos públicos y un proxy diferido ya cargado en el contexto no se inicializa al leerlos.
        return em.createQuery("select p.nombre, p.descripcion, p.precio, p.imagenVersion, p.categoriaId from ProductoEntity p where p.id = :id", Tuple.class)
                .setParameter("id", id).getResultStream().findFirst()
                .map(t -> new Producto(id, t.get(0, String.class), t.get(1, String.class), t.get(2, BigDecimal.class),
                        t.get(3, String.class), t.get(4, Integer.class)));
    }

    private static Producto producto(StockEntity fila) {
        return new Producto(fila.id, fila.producto.nombre, fila.producto.descripcion, fila.producto.precio, fila.producto.imagenVersion, fila.producto.categoriaId);
    }

    @Override public Map<Integer, Stock> cargarInventario() {
        Map<Integer, Stock> stocks = new LinkedHashMap<>();
        for (StockEntity fila : em.createQuery("select s from StockEntity s join fetch s.producto order by s.id", StockEntity.class).getResultList()) {
            Producto producto = producto(fila);
            Stock stock = switch (tipo(fila.tipo)) {
                case SIMPLE -> new StockSimple(producto, fila.stockFisico, fila.stockExhibicion, fila.stockReservado);
                case COMBO -> new ComboStock(producto,
                        fila.stockFisico == null ? 0 : fila.stockFisico,
                        fila.stockExhibicion == null ? 0 : fila.stockExhibicion,
                        fila.stockReservado == null ? 0 : fila.stockReservado,
                        fila.stockReservadoVirtual, new ReservaFisicoPrimero());
            };
            stocks.put(fila.id, stock);
        }
        // Una sola instancia por identidad, también cuando hay ramas compartidas.
        for (LineaComboEntity fila : em.createQuery("select l from LineaComboEntity l order by l.id.comboId, l.id.componenteId", LineaComboEntity.class).getResultList()) {
            ((ComboStock) stocks.get(fila.id.comboId)).agregar(stocks.get(fila.id.componenteId), fila.cantidadEnCombo);
        }
        return stocks;
    }

    private static Stock.Tipo tipo(String discriminador) {
        return Arrays.stream(Stock.Tipo.values()).filter(t -> t.name().equals(discriminador)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Discriminador inválido: " + discriminador));
    }

    @Override public void guardarEstado(Stock stock) {
        StockEntity fila = em.find(StockEntity.class, stock.getProducto().getId());
        if (fila == null) throw new EntidadNoEncontradaException("Stock", stock.getProducto().getId());
        fila.stockFisico = stock.getStockFisico();
        fila.stockExhibicion = stock.getStockExhibicion();
        fila.stockReservado = stock.getStockReservado();
        fila.stockReservadoVirtual = stock.getStockReservadoVirtual();
    }

    @Override public Stock crear(AltaProducto datos, boolean combo) {
        ProductoEntity producto = new ProductoEntity();
        producto.categoriaId = datos.categoriaId();
        producto.nombre = datos.nombre(); producto.descripcion = datos.descripcion();
        producto.precio = datos.precio();
        producto.imagenVersion = datos.imagen() == null ? null : datos.imagen().version();
        em.persist(producto);
        if (datos.imagen() != null) {
            ImagenProductoEntity imagen = new ImagenProductoEntity();
            imagen.productoId = producto.id; imagen.tipo = datos.imagen().tipo(); imagen.contenido = datos.imagen().contenido();
            em.persist(imagen);
        }
        StockEntity fila = new StockEntity();
        fila.producto = producto; fila.id = producto.id; fila.tipo = (combo ? Stock.Tipo.COMBO : Stock.Tipo.SIMPLE).name();
        fila.stockFisico = 0; fila.stockExhibicion = 0; fila.stockReservado = 0;
        em.persist(fila);
        Producto dominio = new Producto(producto.id, datos.nombre(), datos.descripcion(), datos.precio(), producto.imagenVersion, producto.categoriaId);
        return combo ? new ComboStock(dominio) : new StockSimple(dominio, 0, 0, 0);
    }
    @Override public java.util.Optional<ImagenProducto> consultarImagen(int id) {
        ImagenProductoEntity imagen = em.find(ImagenProductoEntity.class, id);
        return imagen == null ? java.util.Optional.empty() : java.util.Optional.of(new ImagenProducto(imagen.tipo, imagen.contenido));
    }

    @Override public void guardarCategoria(int id, Integer categoriaId) { em.find(ProductoEntity.class, id).categoriaId = categoriaId; }

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
