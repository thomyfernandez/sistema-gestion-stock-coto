package ar.com.coto.stock.domain.model;

/**
 * Patrón Strategy GoF: define la política de asignación de reservas de combos
 * entre stock físico propio armado y componentes virtuales.
 */
public interface EstrategiaReservaCombo {
    record DistribucionReserva(int cantidadPropio, int cantidadVirtual) {
        public DistribucionReserva {
            if (cantidadPropio < 0 || cantidadVirtual < 0) {
                throw new IllegalArgumentException("Las cantidades a reservar no pueden ser negativas");
            }
        }
    }

    /**
     * Calcula cuántas unidades reservar del stock físico propio y cuántas de los componentes virtuales.
     *
     * @param cantidadSolicitada cantidad total de combos a reservar
     * @param disponiblePropio   unidades disponibles en el stock propio armado
     * @param disponibleVirtual  unidades que se pueden armar con los componentes libres
     * @return distribución calculada
     */
    DistribucionReserva calcularReserva(int cantidadSolicitada, int disponiblePropio, int disponibleVirtual);
}

