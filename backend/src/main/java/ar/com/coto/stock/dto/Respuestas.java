package ar.com.coto.stock.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** DTOs planos: no exponen entidades JPA, observadores ni referencias recursivas. */
public final class Respuestas {
    private Respuestas() { }
    public record Componente(int stockId, String nombre, String tipo, int cantidadEnCombo, int disponible) { }
    public record Producto(int id, String nombre, String descripcion, BigDecimal precio, String tipo,
                           int disponible, Integer stockFisico, Integer stockExhibicion, Integer stockReservado,
                           List<Componente> componentes) { }
    public record Pedido(long id, int stockId, Instant fecha, int cantidad, String estado) { }
    public record Movimiento(long id, int stockId, String tipo, int cantidad, Instant fecha, String motivo, String usuario) { }
    public record ResultadoMovimiento(long id, Map<Integer, Integer> disponibilidades, List<Pedido> pedidosGenerados) { }
    public record Auditoria(int stockId, Instant fecha, Integer disponibleAnterior, int disponibleActual, String detalle) { }
    public record Politica(int stockId, String tipo, int puntoPedido, int stockObjetivo, BigDecimal demandaDiaria,
                           int diasCobertura, BigDecimal factorEstacional, int umbralAlerta, String destinoAlerta) { }
    public record Promocion(long id, int productoId, String tipo, BigDecimal porcentaje, int orden, boolean activa, String medioPago) { }
    public record Precio(int productoId, int cantidad, BigDecimal precioLista, BigDecimal precioFinal,
                         BigDecimal ahorro, List<Long> promocionesAplicadas) { }
    public record ItemCatalogo(int id, String nombre, String descripcion, String tipo, int disponible, Precio precio) { }
    public record Canal(String canal) { }
    public record Evento(long id, String tipo, int stockId, Long pedidoId, String estado, int intentos, String ultimoError, Instant fecha) { }
    public record Mensaje(String canal, String destino, String texto) { }
    public record OrdenERP(String referencia, int productoId, int cantidad, String fecha) { }
    public record Integraciones(List<Evento> entregas, Map<String, Boolean> proveedoresDisponibles,
                                List<Mensaje> mensajes, List<OrdenERP> pedidosERP, Map<String, String> catalogoDigital) { }
    public record Reintento(int procesados) { }
    public record EstadoProveedor(String sistema, boolean disponible) { }
    public record Estado(String aplicacion, String estado, String version) { }
}
