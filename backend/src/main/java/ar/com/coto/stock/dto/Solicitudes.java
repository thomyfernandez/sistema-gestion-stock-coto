package ar.com.coto.stock.dto;

import ar.com.coto.stock.domain.model.Canal;
import ar.com.coto.stock.domain.model.TipoMovimiento;
import ar.com.coto.stock.domain.reposicion.PoliticaReposicion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** Contratos JSON de entrada. Los tipos numéricos requeridos son boxed para detectar campos ausentes. */
public final class Solicitudes {
    private Solicitudes() { }
    /** Subconjunto de movimientos que acepta la app de sucursal; el resto de los contratos usa los enums del dominio. */
    public enum TipoSucursal { TRASLADO_A_GONDOLA, RETORNO_A_DEPOSITO, DEVOLUCION, MERMA_DEPOSITO, MERMA_GONDOLA }

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
            @NotBlank @Size(max = 500) String motivo) { }
    public record MovimientoSucursal(
            @NotNull @Positive Integer stockId,
            @NotNull TipoSucursal tipo,
            @NotNull @Positive Integer cantidad,
            @NotBlank @Size(max = 500) String motivo) { }
    public record Operacion(
            @NotNull @Positive Integer stockId,
            @NotNull @Positive Integer cantidad,
            @NotBlank @Size(max = 500) String motivo) { }
    public record OperacionCombo(
            @NotNull @Positive Integer cantidad,
            @NotBlank @Size(max = 500) String motivo) { }
    public record Login(@NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 100) String password) { }
    /** factorEstacional se acepta e ignora: el factor ahora sale de las temporadas (ver Temporada). */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("factorEstacional")
    public record Politica(
            @NotNull PoliticaReposicion.Tipo tipo,
            @NotNull @PositiveOrZero Integer puntoPedido,
            @NotNull @Positive Integer stockObjetivo,
            @NotNull @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal demandaDiaria,
            @NotNull @Positive Integer diasCobertura,
            @NotNull @Positive Integer umbralAlerta,
            @Size(max = 200) String destinoAlerta, @com.fasterxml.jackson.annotation.JsonAlias("proveedorId") @Positive Long contactoId, Canal canalAlerta) { }
    public record Promocion(
            @NotNull ar.com.coto.stock.domain.valorizacion.Promocion.Tipo tipo,
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
    public record Destinatarios(@NotNull @Size(max = 20) List<@NotNull @Size(max = 200) String> emails) { }
    public record Disponibilidad(@NotNull Boolean disponible) { }
    /** Día y mes, sin año: la temporada se repite cada año. Indicar uno o más artículos simples o una categoría, no ambos. */
    public record Temporada(
            @NotBlank @Size(max = 80) String nombre,
            @Size(max = 200) List<@NotNull @Positive Integer> stockIds, @Positive Integer categoriaId,
            @NotNull @Min(1) @Max(31) Integer desdeDia, @NotNull @Min(1) @Max(12) Integer desdeMes,
            @NotNull @Min(1) @Max(31) Integer hastaDia, @NotNull @Min(1) @Max(12) Integer hastaMes,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("10") @Digits(integer = 2, fraction = 4) BigDecimal factor) { }
}
