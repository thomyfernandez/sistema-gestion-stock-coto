package ar.com.coto.stock.domain.model;

import java.util.Objects;

/** Leaf: físico incluye las unidades de góndola y las reservadas. */
public final class StockSimple extends Stock {
    private int stockFisico;
    private int stockExhibicion;
    private int stockReservado;

    public StockSimple(Producto producto, int stockFisico, int stockExhibicion, int stockReservado) {
        super(producto);
        Reglas.noNegativo(stockFisico, "stockFisico");
        Reglas.noNegativo(stockExhibicion, "stockExhibicion");
        Reglas.noNegativo(stockReservado, "stockReservado");
        if ((long) stockExhibicion + stockReservado > stockFisico) {
            throw new IllegalArgumentException("Exhibición + reservado no puede superar el físico");
        }
        this.stockFisico = stockFisico;
        this.stockExhibicion = stockExhibicion;
        this.stockReservado = stockReservado;
    }

    @Override public int getDisponible() {
        synchronized (MONITOR) { return stockFisico - stockExhibicion - stockReservado; }
    }

    public int getStockFisico() { synchronized (MONITOR) { return stockFisico; } }
    public int getStockExhibicion() { synchronized (MONITOR) { return stockExhibicion; } }
    public int getStockReservado() { synchronized (MONITOR) { return stockReservado; } }

    @Override public void reservar(int cant) {
        synchronized (MONITOR) {
            validarDisponible(cant);
            reservarSinNotificar(cant);
            notificar();
        }
    }

    @Override public void liberarReserva(int cant) {
        synchronized (MONITOR) {
            validarReservado(cant);
            liberarSinNotificar(cant);
            notificar();
        }
    }

    @Override public void despacharReserva(int cant) {
        synchronized (MONITOR) {
            validarReservado(cant);
            despacharSinNotificar(cant);
            notificar();
        }
    }

    public void aplicarMovimiento(MovimientoStock m) {
        Objects.requireNonNull(m);
        if (m.getStockId() != producto.getId()) {
            throw new IllegalArgumentException("El movimiento corresponde a otro stock");
        }
        synchronized (MONITOR) {
            int cantidad = m.getCantidad();
            switch (m.getTipo()) {
                case RECEPCION, DEVOLUCION -> stockFisico = Math.addExact(stockFisico, cantidad);
                case TRASLADO_A_GONDOLA -> {
                    validarDisponible(cantidad);
                    stockExhibicion += cantidad;
                }
                case RETORNO_A_DEPOSITO -> {
                    validarExhibicion(cantidad);
                    stockExhibicion -= cantidad;
                }
                case MERMA_DEPOSITO -> {
                    validarDisponible(cantidad);
                    stockFisico -= cantidad;
                }
                case MERMA_GONDOLA -> {
                    validarExhibicion(cantidad);
                    stockExhibicion -= cantidad;
                    stockFisico -= cantidad;
                }
                case RESERVA_DIGITAL -> { reservar(cantidad); return; }
                case CANCELACION_RESERVA -> { liberarReserva(cantidad); return; }
                case DESPACHO_DIGITAL -> { despacharReserva(cantidad); return; }
            }
            notificar();
        }
    }

    void validarDisponible(int cant) {
        Reglas.positivo(cant, "cantidad");
        if (cant > getDisponible()) {
            throw new StockInsuficienteException("Disponible insuficiente para " + producto.getNombre());
        }
    }

    void validarReservado(int cant) {
        Reglas.positivo(cant, "cantidad");
        if (cant > stockReservado) {
            throw new StockInsuficienteException("Reserva insuficiente para " + producto.getNombre());
        }
    }

    private void validarExhibicion(int cant) {
        if (cant > stockExhibicion) throw new StockInsuficienteException("Exhibición insuficiente");
    }

    // Sólo el Composite del mismo paquete accede a estas operaciones tras validar todo el plan.
    void reservarSinNotificar(int cant) { stockReservado += cant; }
    void liberarSinNotificar(int cant) { stockReservado -= cant; }
    void despacharSinNotificar(int cant) { stockReservado -= cant; stockFisico -= cant; }
}
