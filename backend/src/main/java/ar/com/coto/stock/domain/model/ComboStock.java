package ar.com.coto.stock.domain.model;

import ar.com.coto.stock.domain.observer.ObservadorStock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Composite: representa un combo de productos.
 * Soporta stock propio armado físicamente (canastas/cajas preparadas) y disponibilidad virtual calculada
 * desde sus componentes. La prioridad de reserva se desacopla mediante el patrón Strategy (EstrategiaReservaCombo).
 */
public final class ComboStock extends Stock implements ObservadorStock {
    private final List<LineaCombo> componentes = new ArrayList<>();
    private final List<ComboStock> padres = new ArrayList<>();
    private int stockFisicoPropio;
    private int stockExhibicionPropio;
    private int stockReservadoPropio;
    private EstrategiaReservaCombo estrategiaReserva;

    public ComboStock(Producto producto) {
        this(producto, 0, 0, 0, new ReservaFisicoPrimero());
    }

    public ComboStock(Producto producto, int stockFisicoPropio, int stockExhibicionPropio, int stockReservadoPropio) {
        this(producto, stockFisicoPropio, stockExhibicionPropio, stockReservadoPropio, new ReservaFisicoPrimero());
    }

    public ComboStock(Producto producto, int stockFisicoPropio, int stockExhibicionPropio, int stockReservadoPropio,
                      EstrategiaReservaCombo estrategiaReserva) {
        super(producto);
        Reglas.noNegativo(stockFisicoPropio, "stockFisico");
        Reglas.noNegativo(stockExhibicionPropio, "stockExhibicion");
        Reglas.noNegativo(stockReservadoPropio, "stockReservado");
        if ((long) stockExhibicionPropio + stockReservadoPropio > stockFisicoPropio) {
            throw new IllegalArgumentException("Exhibición + reservado no puede superar el físico");
        }
        this.stockFisicoPropio = stockFisicoPropio;
        this.stockExhibicionPropio = stockExhibicionPropio;
        this.stockReservadoPropio = stockReservadoPropio;
        this.estrategiaReserva = Objects.requireNonNullElseGet(estrategiaReserva, ReservaFisicoPrimero::new);
    }

    public void agregar(Stock s, int cant) {
        Objects.requireNonNull(s);
        Reglas.positivo(cant, "cantidadEnCombo");
        synchronized (MONITOR) {
            if (contiene(s, producto.getId())) throw new IllegalArgumentException("Un combo no admite ciclos");
            if (componentes.stream().anyMatch(l -> l.getComponente().getProducto().getId()
                    == s.getProducto().getId())) {
                throw new IllegalArgumentException("El componente ya existe en el combo");
            }
            componentes.add(new LineaCombo(s, cant));
            try {
                // Valida también los combos que ya contienen a éste: agregar una
                // línea a un hijo no puede invalidar la identidad o cantidades del padre.
                validarEstructura(new HashSet<>());
            } catch (RuntimeException ex) {
                componentes.remove(componentes.size() - 1);
                throw ex;
            }
            if (s instanceof ComboStock hijo) hijo.padres.add(this);
            s.agregarObservador(this);
            notificar();
        }
    }

    public List<LineaCombo> getComponentes() {
        synchronized (MONITOR) { return List.copyOf(componentes); }
    }

    public int getStockFisico() { synchronized (MONITOR) { return stockFisicoPropio; } }
    public int getStockExhibicion() { synchronized (MONITOR) { return stockExhibicionPropio; } }
    public int getStockReservado() { synchronized (MONITOR) { return stockReservadoPropio; } }

    public int getDisponiblePropio() {
        synchronized (MONITOR) { return stockFisicoPropio - stockExhibicionPropio - stockReservadoPropio; }
    }

    public int getDisponibleVirtual() {
        synchronized (MONITOR) {
            Map<StockSimple, Integer> necesidades = requerimientos();
            if (necesidades.isEmpty() || tieneComboVacio(this)) return 0;
            return necesidades.entrySet().stream()
                    .mapToInt(e -> e.getKey().getDisponible() / e.getValue()).min().orElse(0);
        }
    }

    @Override public int getDisponible() {
        synchronized (MONITOR) {
            return Math.addExact(getDisponiblePropio(), getDisponibleVirtual());
        }
    }

    public EstrategiaReservaCombo getEstrategiaReserva() {
        synchronized (MONITOR) { return estrategiaReserva; }
    }

    public void setEstrategiaReserva(EstrategiaReservaCombo estrategia) {
        synchronized (MONITOR) {
            this.estrategiaReserva = Objects.requireNonNull(estrategia);
        }
    }

    public void armar(int cant) {
        Reglas.positivo(cant, "cantidad");
        synchronized (MONITOR) {
            if (tieneComboVacio(this)) throw new IllegalStateException("El combo está incompleto");
            Map<StockSimple, Integer> necesidades = requerimientos();
            if (necesidades.isEmpty()) throw new IllegalStateException("El combo no tiene componentes");

            // Valida disponibilidad en cada componente simple
            necesidades.forEach((simple, factor) -> {
                int totalRequerido = Math.multiplyExact(cant, factor);
                simple.validarDisponible(totalRequerido);
            });

            // Consume el stock físico de los componentes simples
            necesidades.forEach((simple, factor) -> {
                int totalRequerido = Math.multiplyExact(cant, factor);
                simple.consumirParaArmado(totalRequerido);
            });

            stockFisicoPropio = Math.addExact(stockFisicoPropio, cant);

            necesidades.keySet().forEach(StockSimple::notificar);
            notificar();
        }
    }

    public void desarmar(int cant) {
        Reglas.positivo(cant, "cantidad");
        synchronized (MONITOR) {
            if (tieneComboVacio(this)) throw new IllegalStateException("El combo está incompleto");
            Map<StockSimple, Integer> necesidades = requerimientos();
            if (necesidades.isEmpty()) throw new IllegalStateException("El combo no tiene componentes");

            if (cant > getDisponiblePropio()) {
                throw new StockInsuficienteException("Stock propio disponible insuficiente para desarmar el combo");
            }

            stockFisicoPropio -= cant;

            // Restituye el stock físico a los componentes simples
            necesidades.forEach((simple, factor) -> {
                int totalRestituir = Math.multiplyExact(cant, factor);
                simple.restituirPorDesarmado(totalRestituir);
            });

            necesidades.keySet().forEach(StockSimple::notificar);
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
                case ARMADO_COMBO -> armar(cantidad);
                case DESARMADO_COMBO -> desarmar(cantidad);
                case RESERVA_DIGITAL -> reservar(cantidad);
                case CANCELACION_RESERVA -> liberarReserva(cantidad);
                case DESPACHO_DIGITAL -> despacharReserva(cantidad);
                default -> throw new IllegalArgumentException("Los combos solo admiten armado, desarmado, reservas y despachos");
            }
        }
    }

    @Override public void reservar(int cant) {
        Reglas.positivo(cant, "cantidad");
        synchronized (MONITOR) {
            if (tieneComboVacio(this)) throw new IllegalStateException("El combo está incompleto");
            int dispPropio = getDisponiblePropio();
            int dispVirtual = getDisponibleVirtual();

            var distribucion = estrategiaReserva.calcularReserva(cant, dispPropio, dispVirtual);

            if (distribucion.cantidadPropio() > dispPropio) {
                throw new StockInsuficienteException("Stock propio insuficiente para reservar");
            }

            if (distribucion.cantidadVirtual() > 0) {
                operarVirtual(distribucion.cantidadVirtual(), Operacion.RESERVAR);
            }
            if (distribucion.cantidadPropio() > 0) {
                stockReservadoPropio = Math.addExact(stockReservadoPropio, distribucion.cantidadPropio());
                notificar();
            }
        }
    }

    @Override public void liberarReserva(int cant) {
        Reglas.positivo(cant, "cantidad");
        synchronized (MONITOR) {
            int delPropio = Math.min(cant, stockReservadoPropio);
            int delVirtual = cant - delPropio;
            if (delPropio > 0) {
                stockReservadoPropio -= delPropio;
            }
            if (delVirtual > 0) {
                operarVirtual(delVirtual, Operacion.LIBERAR);
            }
            if (delPropio > 0) {
                notificar();
            }
        }
    }

    @Override public void despacharReserva(int cant) {
        Reglas.positivo(cant, "cantidad");
        synchronized (MONITOR) {
            int delPropio = Math.min(cant, stockReservadoPropio);
            int delVirtual = cant - delPropio;
            if (delPropio > 0) {
                stockReservadoPropio -= delPropio;
                stockFisicoPropio -= delPropio;
            }
            if (delVirtual > 0) {
                operarVirtual(delVirtual, Operacion.DESPACHAR);
            }
            if (delPropio > 0) {
                notificar();
            }
        }
    }

    @Override public void actualizar(Stock s) { notificar(); }

    private void operarVirtual(int cant, Operacion operacion) {
        Map<StockSimple, Integer> plan = new LinkedHashMap<>();
        requerimientos().forEach((stock, cantidad) -> plan.put(stock, Math.multiplyExact(cantidad, cant)));
        // Primero valida TODOS los componentes. Ninguno cambia si falta una unidad.
        plan.forEach((stock, cantidad) -> {
            if (operacion == Operacion.RESERVAR) stock.validarDisponible(cantidad);
            else stock.validarReservado(cantidad);
        });
        plan.forEach((stock, cantidad) -> {
            switch (operacion) {
                case RESERVAR -> stock.reservarSinNotificar(cantidad);
                case LIBERAR -> stock.liberarSinNotificar(cantidad);
                case DESPACHAR -> stock.despacharSinNotificar(cantidad);
            }
        });
        // Los observadores siempre ven el plan completo aplicado. La propagación
        // a combos padres se realiza mediante actualizar, sin notificar estados parciales.
        plan.keySet().forEach(StockSimple::notificar);
    }

    private Map<StockSimple, Integer> requerimientos() {
        Map<StockSimple, Integer> resultado = new LinkedHashMap<>();
        acumular(this, 1, resultado);
        return resultado;
    }

    private void validarEstructura(Set<ComboStock> visitados) {
        if (!visitados.add(this)) return;
        verificarIdentidades(this, new LinkedHashMap<>());
        requerimientos();
        padres.forEach(padre -> padre.validarEstructura(visitados));
    }

    private static void acumular(Stock stock, int factor, Map<StockSimple, Integer> resultado) {
        if (stock instanceof StockSimple simple) {
            resultado.merge(simple, factor, Math::addExact);
        } else if (stock instanceof ComboStock combo) {
            for (LineaCombo linea : combo.componentes) {
                acumular(linea.getComponente(), Math.multiplyExact(factor, linea.getCantidadEnCombo()), resultado);
            }
        } else {
            throw new IllegalArgumentException("Tipo de stock desconocido");
        }
    }

    private static boolean contiene(Stock stock, int productoId) {
        if (stock.getProducto().getId() == productoId) return true;
        return stock instanceof ComboStock combo && combo.componentes.stream()
                .anyMatch(l -> contiene(l.getComponente(), productoId));
    }

    private static boolean tieneComboVacio(ComboStock combo) {
        return combo.componentes.isEmpty() || combo.componentes.stream().anyMatch(l ->
                l.getComponente() instanceof ComboStock hijo && tieneComboVacio(hijo));
    }

    private static void verificarIdentidades(Stock stock, Map<Integer, Stock> identidades) {
        Stock anterior = identidades.putIfAbsent(stock.getProducto().getId(), stock);
        if (anterior != null) {
            if (anterior != stock) throw new IllegalArgumentException("Dos instancias para un mismo stock");
            return;
        }
        if (stock instanceof ComboStock combo) {
            combo.componentes.forEach(l -> verificarIdentidades(l.getComponente(), identidades));
        }
    }

    private enum Operacion { RESERVAR, LIBERAR, DESPACHAR }
}
