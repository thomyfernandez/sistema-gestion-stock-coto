package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.reposicion.*;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Context de Strategy: selección explícita y variante sin estado compartido para políticas por SKU. */
public final class ServicioReposicion {
    private final AtomicReference<EstrategiaReposicion> estrategia;
    private final Clock reloj;
    public ServicioReposicion(EstrategiaReposicion estrategia, Clock reloj) {
        this.estrategia = new AtomicReference<>(Objects.requireNonNull(estrategia));
        this.reloj = Objects.requireNonNull(reloj);
    }
    public void setEstrategia(EstrategiaReposicion e) { estrategia.set(Objects.requireNonNull(e)); }
    public PedidoReposicion generarPedido(Stock s) {
        return planificar(s, estrategia.get(), 0).orElseThrow(() -> new IllegalStateException("No se necesita reposición"));
    }
    public Optional<PedidoReposicion> planificar(Stock s, EstrategiaReposicion politica, int cantidadPendiente) {
        if (!(Objects.requireNonNull(s) instanceof StockSimple)) throw new IllegalArgumentException("Reponer componentes simples, no combos");
        Reglas.noNegativo(cantidadPendiente, "cantidadPendiente");
        int propuesta = Objects.requireNonNull(politica).calcularCantidad(s);
        Reglas.noNegativo(propuesta, "propuesta de reposición");
        int cantidad = Math.max(0, propuesta - cantidadPendiente);
        return cantidad == 0 ? Optional.empty() : Optional.of(new PedidoReposicion(s.getProducto().getId(), Date.from(reloj.instant()), cantidad));
    }
}
