package ar.com.coto.stock.domain.model;

/**
 * Estrategia Strategy por defecto: prioriza consumir las canastas/combos ya armados físicamente en salón/depósito.
 * El remanente que no alcance con stock propio se reserva de los componentes virtuales.
 */
public final class ReservaFisicoPrimero implements EstrategiaReservaCombo {
    @Override
    public DistribucionReserva calcularReserva(int cantidadSolicitada, int disponiblePropio, int disponibleVirtual) {
        Reglas.positivo(cantidadSolicitada, "cantidad");
        int delPropio = Math.min(cantidadSolicitada, Math.max(0, disponiblePropio));
        int delVirtual = cantidadSolicitada - delPropio;
        return new DistribucionReserva(delPropio, delVirtual);
    }
}
