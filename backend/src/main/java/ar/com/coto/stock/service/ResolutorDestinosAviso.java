package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.RepositorioCategorias;
import ar.com.coto.stock.domain.port.RepositorioContactos;
import ar.com.coto.stock.domain.port.RepositorioDestinatarios;
import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import java.util.List;
import java.util.Objects;

/** Decide a quién avisar por stock bajo. Se usa dentro de la transacción que encola el aviso. */
public final class ResolutorDestinosAviso {
    /** Se resuelve al crear el evento: posteriores cambios del contacto no alteran la outbox. */
    public record DestinoAviso(Canal canal, String destino) { }

    private final RepositorioContactos contactos;
    private final RepositorioCategorias categorias;
    private final RepositorioDestinatarios destinatarios;

    public ResolutorDestinosAviso(RepositorioContactos contactos, RepositorioCategorias categorias, RepositorioDestinatarios destinatarios) {
        this.contactos = Objects.requireNonNull(contactos);
        this.categorias = Objects.requireNonNull(categorias);
        this.destinatarios = Objects.requireNonNull(destinatarios);
    }

    /** Lista de la categoría, lista general y, si ambas están vacías, la asignación anterior por contacto o sucursal. */
    public List<DestinoAviso> resolver(PoliticaReposicion politica, Producto producto, ConfiguracionAvisos general) {
        if (producto.getCategoriaId() != null) {
            var lista = destinatarios.obtener(producto.getCategoriaId());
            if (!lista.vacia()) return porEmail(lista);
        }
        var listaGeneral = destinatarios.obtener(null);
        if (!listaGeneral.vacia()) return porEmail(listaGeneral);
        return List.of(respaldo(politica, producto, general));
    }

    /** Destino previo a las listas: contacto del artículo, contacto de la categoría y destinatario general. */
    public DestinoAviso respaldo(PoliticaReposicion politica, Producto producto, ConfiguracionAvisos general) {
        if (politica.contactoId() != null) return porContacto(politica.contactoId(), politica.canalAlerta());
        if (producto.getCategoriaId() != null) {
            var categoria = categorias.obtener(producto.getCategoriaId())
                    .orElseThrow(() -> new EntidadNoEncontradaException("Categoría", producto.getCategoriaId()));
            if (categoria.contactoId() != null) return porContacto(categoria.contactoId(), categoria.canalAlerta());
        }
        return new DestinoAviso(general.canal(), general.destino());
    }

    private DestinoAviso porContacto(long contactoId, Canal canal) {
        var contacto = contactos.obtener(contactoId).orElseThrow(() -> new EntidadNoEncontradaException("Contacto", contactoId));
        return new DestinoAviso(canal, contacto.destino(canal));
    }

    private static List<DestinoAviso> porEmail(ListaDestinatarios lista) {
        return lista.emails().stream().map(email -> new DestinoAviso(Canal.EMAIL, email)).toList();
    }
}
