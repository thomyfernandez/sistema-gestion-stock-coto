package ar.com.coto.stock.domain.model;

import java.math.BigDecimal;

public record AltaProducto(String nombre, String descripcion, BigDecimal precio, ImagenProducto imagen, Integer categoriaId) {
    /** Límite de la columna NUMERIC(15,2): trece dígitos enteros. */
    private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("1E13");
    private static final int MAX_NOMBRE = 160;
    private static final int MAX_DESCRIPCION = 500;

    public AltaProducto(String nombre, String descripcion, BigDecimal precio) { this(nombre, descripcion, precio, null, null); }
    public AltaProducto(String nombre, String descripcion, BigDecimal precio, ImagenProducto imagen) { this(nombre, descripcion, precio, imagen, null); }
    public AltaProducto {
        if (categoriaId != null) Reglas.positivo(categoriaId, "categoriaId");
        nombre = Reglas.texto(nombre, "nombre");
        descripcion = Reglas.texto(descripcion, "descripcion");
        Reglas.noNegativo(precio, "precio");
        if (nombre.length() > MAX_NOMBRE || descripcion.length() > MAX_DESCRIPCION || precio.compareTo(PRECIO_MAXIMO) >= 0) {
            throw new IllegalArgumentException("Datos fuera del tamaño admitido por el catálogo");
        }
        if (precio.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El precio admite hasta dos decimales");
        }
    }
}
