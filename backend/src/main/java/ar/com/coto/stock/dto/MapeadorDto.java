package ar.com.coto.stock.dto;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import ar.com.coto.stock.domain.valorizacion.Promocion;
import ar.com.coto.stock.service.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

public final class MapeadorDto {
    private MapeadorDto() { }
    public static AltaProducto alta(Solicitudes.Producto s) { return new AltaProducto(s.nombre(), s.descripcion(), s.precio().doubleValue()); }
    public static AltaProducto alta(Solicitudes.Combo s) { return new AltaProducto(s.nombre(), s.descripcion(), s.precio().doubleValue()); }
    public static List<ComponenteCombo> componentes(Solicitudes.Combo s) {
        return s.componentes().stream().map(c -> new ComponenteCombo(c.stockId(), c.cantidad())).toList();
    }
    public static Respuestas.Producto producto(Stock s) {
        List<Respuestas.Componente> componentes = s instanceof ComboStock combo ? combo.getComponentes().stream().map(l ->
                new Respuestas.Componente(l.getComponente().getProducto().getId(), l.getComponente().getProducto().getNombre(),
                        tipo(l.getComponente()), l.getCantidadEnCombo(), l.getComponente().getDisponible())).toList() : List.of();
        StockSimple simple = s instanceof StockSimple hoja ? hoja : null;
        return new Respuestas.Producto(s.getProducto().getId(), s.getProducto().getNombre(), s.getProducto().getDescripcion(),
                BigDecimal.valueOf(s.getProducto().getPrecio()).setScale(2), tipo(s), s.getDisponible(),
                simple == null ? null : simple.getStockFisico(), simple == null ? null : simple.getStockExhibicion(),
                simple == null ? null : simple.getStockReservado(), componentes);
    }
    private static String tipo(Stock s) { return s instanceof StockSimple ? "SIMPLE" : "COMBO"; }
    public static MovimientoStock movimiento(Solicitudes.Movimiento s, Clock reloj) {
        return new MovimientoStock(s.stockId(), TipoMovimiento.valueOf(s.tipo().name()), s.cantidad(), reloj.instant(), s.motivo(), s.usuario());
    }
    public static MovimientoStock movimiento(Solicitudes.MovimientoSucursal s, Clock reloj) {
        return new MovimientoStock(s.stockId(), TipoMovimiento.valueOf(s.tipo().name()), s.cantidad(), reloj.instant(), s.motivo(), s.usuario());
    }
    public static MovimientoStock movimiento(Solicitudes.Operacion s, Solicitudes.TipoMovimiento tipo, Clock reloj) {
        return new MovimientoStock(s.stockId(), TipoMovimiento.valueOf(tipo.name()), s.cantidad(), reloj.instant(), s.motivo(), s.usuario());
    }
    public static Respuestas.Pedido pedido(PedidoRegistrado p) {
        return new Respuestas.Pedido(p.id(), p.pedido().getStockId(), p.pedido().getFecha().toInstant(), p.pedido().getCantidad(), p.pedido().getEstado().name());
    }
    public static Respuestas.Movimiento movimiento(MovimientoRegistrado r) {
        MovimientoStock m = r.movimiento();
        return new Respuestas.Movimiento(r.id(), m.getStockId(), m.getTipo().name(), m.getCantidad(), m.getFecha(), m.getMotivo(), m.getUsuario());
    }
    public static Respuestas.ResultadoMovimiento resultado(GestionInventario.ResultadoMovimiento r) {
        return new Respuestas.ResultadoMovimiento(r.id(), r.disponibilidades(), r.pedidosGenerados().stream().map(MapeadorDto::pedido).toList());
    }
    public static Respuestas.Auditoria auditoria(AuditoriaStock a) {
        return new Respuestas.Auditoria(a.stockId(), a.fecha(), a.disponibleAnterior(), a.disponibleActual(), a.detalle());
    }
    public static PoliticaReposicion politica(int id, Solicitudes.Politica p) {
        return new PoliticaReposicion(id, PoliticaReposicion.Tipo.valueOf(p.tipo().name()), p.puntoPedido(), p.stockObjetivo(),
                p.demandaDiaria().doubleValue(), p.diasCobertura(), p.factorEstacional().doubleValue(), p.umbralAlerta(), p.destinoAlerta());
    }
    public static Respuestas.Politica politica(PoliticaReposicion p) {
        return new Respuestas.Politica(p.stockId(), p.tipo().name(), p.puntoPedido(), p.stockObjetivo(), BigDecimal.valueOf(p.demandaDiaria()),
                p.diasCobertura(), BigDecimal.valueOf(p.factorEstacional()), p.umbralAlerta(), p.destinoAlerta());
    }
    public static Promocion promocion(int productoId, long id, Solicitudes.Promocion p) {
        return new Promocion(id, productoId, Promocion.Tipo.valueOf(p.tipo().name()), p.porcentaje().doubleValue(), p.orden(), p.activa(), p.medioPago());
    }
    public static Respuestas.Promocion promocion(Promocion p) {
        return new Respuestas.Promocion(p.id(), p.productoId(), p.tipo().name(), BigDecimal.valueOf(p.porcentaje()), p.orden(), p.activa(), p.medioPago());
    }
    public static Respuestas.Precio precio(ConsultaPrecios.PrecioCalculado p) {
        return new Respuestas.Precio(p.productoId(), p.cantidad(), p.precioLista(), p.precioFinal(), p.ahorro(), p.promocionesAplicadas());
    }
    public static Respuestas.ItemCatalogo catalogo(ConsultaPrecios.ItemCatalogo p) {
        return new Respuestas.ItemCatalogo(p.id(), p.nombre(), p.descripcion(), p.tipo(), p.disponible(), precio(p.precio()));
    }
    public static Respuestas.Integraciones integraciones(GestionIntegraciones.Estado e) {
        var sistemas = e.sistemas();
        return new Respuestas.Integraciones(e.entregas().stream().map(x -> new Respuestas.Evento(x.id(), x.tipo(), x.stockId(), x.pedidoId(),
                x.estado(), x.intentos(), x.ultimoError(), x.fecha())).toList(),
                sistemas.disponibles().entrySet().stream().collect(Collectors.toMap(x -> x.getKey().name(), java.util.Map.Entry::getValue)),
                sistemas.mensajes().stream().map(m -> new Respuestas.Mensaje(m.canal(), m.destino(), m.texto())).toList(),
                sistemas.ordenes().stream().map(p -> new Respuestas.OrdenERP(p.referencia(), p.productoId(), p.cantidad(), p.fecha())).toList(),
                sistemas.catalogoDigital());
    }
}
