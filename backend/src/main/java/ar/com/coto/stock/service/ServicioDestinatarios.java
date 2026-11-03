package ar.com.coto.stock.service;
import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.*;
import java.util.List;

/** Listas de emails para los avisos de stock bajo, por categoría y general. */
public final class ServicioDestinatarios implements GestionDestinatarios {
    private final RepositorioDestinatarios destinatarios;
    private final RepositorioCategorias categorias;
    private final RepositorioStock stocks;
    private final UnidadDeTrabajo unidad;
    public ServicioDestinatarios(RepositorioDestinatarios destinatarios, RepositorioCategorias categorias, RepositorioStock stocks, UnidadDeTrabajo unidad) {
        this.destinatarios = destinatarios; this.categorias = categorias; this.stocks = stocks; this.unidad = unidad;
    }
    public List<ListaDestinatarios> listar() { return unidad.consultar(destinatarios::listar); }
    /** Reemplaza la lista completa. Comparte el bloqueo de inventario con la creación de avisos. */
    public ListaDestinatarios guardar(ListaDestinatarios lista) {
        return unidad.ejecutar(() -> {
            stocks.bloquearInventario();
            if (!lista.esGeneral()) categorias.obtener(lista.categoriaId())
                    .orElseThrow(() -> new EntidadNoEncontradaException("Categoría", lista.categoriaId()));
            destinatarios.reemplazar(lista);
            return lista;
        });
    }
}
