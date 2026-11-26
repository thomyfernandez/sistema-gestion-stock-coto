package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.*;
import java.util.List;

/** Alta y edición de contactos. Un cambio no puede dejar sin dato a un canal que ya los usa. */
public final class ServicioContactos implements GestionContactos {
    private final RepositorioContactos contactos;
    private final RepositorioPoliticas politicas;
    private final RepositorioStock stocks;
    private final UnidadDeTrabajo unidad;
    private final RepositorioCategorias categorias;

    public ServicioContactos(RepositorioContactos contactos, RepositorioPoliticas politicas, RepositorioStock stocks,
                             UnidadDeTrabajo unidad, RepositorioCategorias categorias) {
        this.contactos = contactos; this.politicas = politicas; this.stocks = stocks; this.unidad = unidad;
        this.categorias = categorias;
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
                    if (usaElContactoSinSuDato(p.contactoId(), p.canalAlerta(), contacto)) {
                        throw new IllegalArgumentException("El artículo " + p.stockId() + " usa " + p.canalAlerta()
                                + ". Conservá ese dato de contacto o cambiá primero el canal del artículo.");
                    }
                }
                for (var categoria : categorias.listar()) {
                    if (usaElContactoSinSuDato(categoria.contactoId(), categoria.canalAlerta(), contacto)) {
                        throw new IllegalArgumentException("La categoría " + categoria.nombre() + " usa " + categoria.canalAlerta()
                                + ". Conservá ese dato de contacto o cambiá su configuración.");
                    }
                }
            }
            return contactos.guardar(contacto);
        });
    }

    private static boolean usaElContactoSinSuDato(Long contactoId, Canal canal, Contacto contacto) {
        return Long.valueOf(contacto.id()).equals(contactoId) && !contacto.admite(canal);
    }
}
