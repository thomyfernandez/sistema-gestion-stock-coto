package ar.com.coto.stock.domain.port;

/** Puerto de salida que implementará el adaptador de Coto Digital. */
@FunctionalInterface
public interface PublicadorDisponibilidad {
    void sincronizar(int productoId, int disponible);
}
