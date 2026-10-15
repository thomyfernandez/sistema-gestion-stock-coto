package ar.com.coto.stock.domain.port;

import java.util.function.Supplier;

public interface UnidadDeTrabajo {
    <T> T ejecutar(Supplier<T> trabajo);
    <T> T consultar(Supplier<T> consulta);
    <T> T ejecutarNueva(Supplier<T> trabajo);
    void despuesDeConfirmar(Runnable accion);
}
