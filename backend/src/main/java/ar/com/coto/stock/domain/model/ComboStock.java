package ar.com.coto.stock.domain.model;

import ar.com.coto.stock.domain.observer.ObservadorStock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Composite: no posee contadores propios. También observa cambios de sus componentes. */
public final class ComboStock extends Stock implements ObservadorStock {
    private final List<LineaCombo> componentes = new ArrayList<>();
    private final List<ComboStock> padres = new ArrayList<>();

    public ComboStock(Producto producto) { super(producto); }

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

    @Override public int getDisponible() {
        synchronized (MONITOR) {
            Map<StockSimple, Integer> necesidades = requerimientos();
            if (necesidades.isEmpty() || tieneComboVacio(this)) return 0;
            return necesidades.entrySet().stream()
                    .mapToInt(e -> e.getKey().getDisponible() / e.getValue()).min().orElse(0);
        }
    }

    @Override public void reservar(int cant) { operar(cant, Operacion.RESERVAR); }
    @Override public void liberarReserva(int cant) { operar(cant, Operacion.LIBERAR); }
    @Override public void despacharReserva(int cant) { operar(cant, Operacion.DESPACHAR); }
    @Override public void actualizar(Stock s) { notificar(); }

    private void operar(int cant, Operacion operacion) {
        Reglas.positivo(cant, "cantidad");
        synchronized (MONITOR) {
            if (tieneComboVacio(this)) throw new IllegalStateException("El combo está incompleto");
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
