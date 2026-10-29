package ar.com.coto.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** Contratos JSON de entrada. Los tipos numéricos requeridos son boxed para detectar campos ausentes. */
public final class Solicitudes {
    private Solicitudes() { }
    public enum TipoMovimiento {
        RECEPCION, TRASLADO_A_GONDOLA, RETORNO_A_DEPOSITO, DEVOLUCION, MERMA_DEPOSITO,
        MERMA_GONDOLA, RESERVA_DIGITAL, CANCELACION_RESERVA, DESPACHO_DIGITAL
    }
    public enum TipoSucursal { TRASLADO_A_GONDOLA, RETORNO_A_DEPOSITO, DEVOLUCION, MERMA_DEPOSITO, MERMA_GONDOLA }
    public enum Estrategia { PUNTO_PEDIDO, DEMANDA, ESTACIONAL }
    public enum TipoPromocion { DESCUENTO, DOS_POR_UNO, MEDIO_PAGO }
    public enum Canal { EMAIL, SMS, WHATSAPP }
    public enum Sistema { ERP, MENSAJERIA, COTO_DIGITAL }
    public enum TipoProducto { SIMPLE, COMBO }
    public enum EstadoPedido { PENDIENTE, ENVIADO, RECIBIDO, CANCELADO }

    public record Producto(
            @NotBlank @Size(max = 160) String nombre,
            @NotBlank @Size(max = 500) String descripcion,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal precio,
            @Valid Imagen imagen, @Positive Integer categoriaId) { }
    public record Imagen(@NotBlank @Pattern(regexp = "image/(png|jpeg)") String tipo,
                         @NotBlank @Size(max = 2796204) String contenido) { }
    public record Componente(@NotNull @Positive Integer stockId, @NotNull @Positive Integer cantidad) { }
    public record Combo(
            @NotBlank @Size(max = 160) String nombre,
            @NotBlank @Size(max = 500) String descripcion,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal precio,
            @NotEmpty List<@NotNull @Valid Componente> componentes, @Valid Imagen imagen, @Positive Integer categoriaId) { }
    public record Movimiento(
            @NotNull @Positive Integer stockId,
            @NotNull TipoMovimiento tipo,
            @NotNull @Positive Integer cantidad,
            @NotBlank @Size(max = 500) String motivo,
            @NotBlank @Size(max = 100) String usuario) { }
    public record MovimientoSucursal(
            @NotNull @Positive Integer stockId,
            @NotNull TipoSucursal tipo,
            @NotNull @Positive Integer cantidad,
            @NotBlank @Size(max = 500) String motivo,
            @NotBlank @Size(max = 100) String usuario) { }
    public record Operacion(
            @NotNull @Positive Integer stockId,
            @NotNull @Positive Integer cantidad,
            @NotBlank @Size(max = 500) String motivo,
            @NotBlank @Size(max = 100) String usuario) { }
    public record Usuario(@NotBlank @Size(max = 100) String usuario) { }
    public record Politica(
            @NotNull Estrategia tipo,
            @NotNull @PositiveOrZero Integer puntoPedido,
            @NotNull @Positive Integer stockObjetivo,
            @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal demandaDiaria,
            @NotNull @Positive Integer diasCobertura,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 8, fraction = 4) BigDecimal factorEstacional,
            @NotNull @Positive Integer umbralAlerta,
            @Size(max = 200) String destinoAlerta, @com.fasterxml.jackson.annotation.JsonAlias("proveedorId") @Positive Long contactoId, Canal canalAlerta) { }
    public record Promocion(
            @NotNull TipoPromocion tipo,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal porcentaje,
            @NotNull @PositiveOrZero Integer orden,
            @NotNull Boolean activa,
            @Size(max = 60) String medioPago) { }
    public record ConfiguracionCanal(@NotNull Canal canal) { }
    public record ConfiguracionAvisos(@NotNull Canal canal, @NotBlank @Email @Size(max = 200) String email,
                                     @NotNull @Size(max = 30) String telefono) { }
    public record Contacto(@NotBlank @Size(max = 160) String nombre,
                            @Size(max = 200) String email, @Size(max = 30) String telefono) { }
    public record Categoria(@NotBlank @Size(max = 80) String nombre, @Positive Long contactoId, Canal canalAlerta) { }
    public record CategoriaProducto(@Positive Integer categoriaId) { }
    public record Disponibilidad(@NotNull Boolean disponible) { }
}
