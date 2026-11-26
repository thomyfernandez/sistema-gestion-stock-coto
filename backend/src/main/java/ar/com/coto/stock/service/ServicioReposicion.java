package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.RepositorioTemporadas;
import ar.com.coto.stock.domain.reposicion.*;
import java.time.Clock;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Context de Strategy: selección explícita y variante sin estado compartido para políticas por SKU. */
public final class ServicioReposicion {
    private final AtomicReference<EstrategiaReposicion> estrategia;
    private final Clock reloj;
    private final RepositorioTemporadas temporadas;
    public ServicioReposicion(EstrategiaReposicion estrategia, Clock reloj) {
        this(estrategia, reloj, RepositorioTemporadas.VACIO);
    }
    public ServicioReposicion(EstrategiaReposicion estrategia, Clock reloj, RepositorioTemporadas temporadas) {
        this.estrategia = new AtomicReference<>(Objects.requireNonNull(estrategia));
        this.reloj = Objects.requireNonNull(reloj);
        this.temporadas = Objects.requireNonNull(temporadas);
    }
    public void setEstrategia(EstrategiaReposicion e) { estrategia.set(Objects.requireNonNull(e)); }
    public PedidoReposicion generarPedido(Stock s) {
        return planificar(s, estrategia.get(), 0).orElseThrow(() -> new EstadoInvalidoException("No se necesita reposición"));
    }
    /** Estrategia concreta de la política del artículo. La estacional recibe las temporadas del artículo y
     * de su categoría, y el reloj del servicio para saber cuál rige. */
    public EstrategiaReposicion estrategiaPara(PoliticaReposicion politica, Stock s) {
        Objects.requireNonNull(politica); Objects.requireNonNull(s);
        if (politica.tipo() != PoliticaReposicion.Tipo.ESTACIONAL) return estrategiaPara(politica, s, List.of());
        return estrategiaPara(politica, s, temporadas.listarPara(s.getProducto().getId(), s.getProducto().getCategoriaId()));
    }
    /** Variante para evaluar muchos artículos: recibe todas las temporadas, leídas una sola vez con {@link #temporadas()}. */
    public EstrategiaReposicion estrategiaPara(PoliticaReposicion politica, Stock s, Collection<Temporada> todas) {
        Objects.requireNonNull(politica); Objects.requireNonNull(s);
        return politica.crearEstrategia(CalendarioTemporadas.para(s.getProducto().getId(), s.getProducto().getCategoriaId(), todas), reloj);
    }
    public List<Temporada> temporadas() { return temporadas.listar(); }
    public Optional<PedidoReposicion> planificar(Stock s, EstrategiaReposicion politica, int cantidadPendiente) {
        if (Objects.requireNonNull(s).getTipo() != Stock.Tipo.SIMPLE) throw new IllegalArgumentException("Reponer componentes simples, no combos");
        Reglas.noNegativo(cantidadPendiente, "cantidadPendiente");
        int propuesta = Objects.requireNonNull(politica).calcularCantidad(s);
        Reglas.noNegativo(propuesta, "propuesta de reposición");
        int cantidad = Math.max(0, propuesta - cantidadPendiente);
        return cantidad == 0 ? Optional.empty() : Optional.of(new PedidoReposicion(s.getProducto().getId(), Date.from(reloj.instant()), cantidad));
    }
}
