package ar.com.coto.stock.service;
import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.*;
import java.util.List;

public final class ServicioCategorias implements GestionCategorias {
    private final RepositorioCategorias categorias;
    private final RepositorioContactos contactos;
    private final RepositorioStock stocks;
    private final UnidadDeTrabajo unidad;
    public ServicioCategorias(RepositorioCategorias categorias, RepositorioContactos contactos, RepositorioStock stocks, UnidadDeTrabajo unidad) {
        this.categorias = categorias; this.contactos = contactos; this.stocks = stocks; this.unidad = unidad;
    }
    public List<Categoria> listar() { return unidad.consultar(categorias::listar); }
    public Categoria consultar(int id) { return unidad.consultar(() -> categorias.obtener(id).orElseThrow(() -> new EntidadNoEncontradaException("Categoría", id))); }
    public Categoria guardar(Categoria categoria) {
        return unidad.ejecutar(() -> {
            stocks.bloquearInventario();
            if (categoria.id() != 0) consultar(categoria.id());
            if (categorias.listar().stream().anyMatch(c -> c.id() != categoria.id() && c.nombre().equalsIgnoreCase(categoria.nombre())))
                throw new IllegalArgumentException("Ya existe una categoría con ese nombre");
            if (categoria.contactoId() != null) contactos.obtener(categoria.contactoId())
                    .orElseThrow(() -> new EntidadNoEncontradaException("Contacto", categoria.contactoId())).destino(categoria.canalAlerta());
            return categorias.guardar(categoria);
        });
    }
}
