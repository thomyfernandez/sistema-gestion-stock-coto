package ar.com.coto.stock.service;
import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.*;
import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import java.util.List;

public final class ServicioContactos implements GestionContactos {
    private final RepositorioContactos contactos;
    private final RepositorioPoliticas politicas;
    private final RepositorioStock stocks;
    private final UnidadDeTrabajo unidad;
    private final RepositorioCategorias categorias;
    public ServicioContactos(RepositorioContactos contactos, RepositorioPoliticas politicas,
                               RepositorioStock stocks, UnidadDeTrabajo unidad, RepositorioCategorias categorias) {
        this.contactos = contactos; this.politicas = politicas; this.stocks = stocks; this.unidad = unidad; this.categorias = categorias;
    }
    public List<Contacto> listar() { return unidad.consultar(contactos::listar); }
    public Contacto consultar(long id) {
        return unidad.consultar(() -> contactos.obtener(id).orElseThrow(() -> new EntidadNoEncontradaException("Contacto", id)));
    }
    public Contacto guardar(Contacto contacto) {
        return unidad.ejecutar(() -> {
            stocks.bloquearInventario();
            if (contacto.id() != 0) {
                consultar(contacto.id());
                for (var p : politicas.listar().values()) {
                    if (Long.valueOf(contacto.id()).equals(p.contactoId())) {
                        try { contacto.destino(p.canalAlerta()); }
                        catch (IllegalArgumentException ex) {
                            throw new IllegalArgumentException("El artículo " + p.stockId() + " usa " + p.canalAlerta()
                                    + ". Conservá ese dato de contacto o cambiá primero el canal del artículo.");
                        }
                    }
                }
            }
            for (var categoria : categorias.listar()) {
                if (Long.valueOf(contacto.id()).equals(categoria.contactoId())) {
                    try { contacto.destino(categoria.canalAlerta()); }
                    catch (IllegalArgumentException ex) { throw new IllegalArgumentException("La categoría " + categoria.nombre() + " usa " + categoria.canalAlerta() + ". Conservá ese dato de contacto o cambiá su configuración."); }
                }
            }
            return contactos.guardar(contacto);
        });
    }
    /** Se resuelve al crear el evento: posteriores cambios del contacto no alteran la outbox. */
    public record DestinoAviso(String canal, String destino) { }
    public DestinoAviso resolver(PoliticaReposicion politica, Producto producto, ConfiguracionAvisos general) {
        if (politica.contactoId() != null) return new DestinoAviso(politica.canalAlerta(), consultar(politica.contactoId()).destino(politica.canalAlerta()));
        if (producto.getCategoriaId() != null) {
            var categoria = categorias.obtener(producto.getCategoriaId()).orElseThrow(() -> new EntidadNoEncontradaException("Categoría", producto.getCategoriaId()));
            if (categoria.contactoId() != null) return new DestinoAviso(categoria.canalAlerta(), consultar(categoria.contactoId()).destino(categoria.canalAlerta()));
        }
        return new DestinoAviso(general.canal(), general.destino());
    }
}
