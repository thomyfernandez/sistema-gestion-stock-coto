package ar.com.coto.stock.domain.model;

/**
 * Estrategia Strategy alternativa: prioriza reservar componentes virtuales, preservando las unidades ya armadas
 * físicamente para la venta directa en salón.
 */
public final class ReservaVirtualPrimero implements EstrategiaReservaCombo {
    @Override
    public DistribucionReserva calcularReserva(int cantidadSolicitada, int disponiblePropio, int disponibleVirtual) {
        Reglas.positivo(cantidadSolicitada, "cantidad");
        int delVirtual = Math.min(cantidadSolicitada, Math.max(0, disponibleVirtual));
        int delPropio = cantidadSolicitada - delVirtual;
        return new DistribucionReserva(delPropio, delVirtual);
    }
}
