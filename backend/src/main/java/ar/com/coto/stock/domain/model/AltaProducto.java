package ar.com.coto.stock.domain.model;

public record AltaProducto(String nombre, String descripcion, double precio, ImagenProducto imagen, Integer categoriaId) {
    public AltaProducto(String nombre, String descripcion, double precio) { this(nombre, descripcion, precio, null, null); }
    public AltaProducto(String nombre, String descripcion, double precio, ImagenProducto imagen) { this(nombre, descripcion, precio, imagen, null); }
    public AltaProducto {
        if (categoriaId != null) Reglas.positivo(categoriaId, "categoriaId");
        nombre = Reglas.texto(nombre, "nombre");
        descripcion = Reglas.texto(descripcion, "descripcion");
        Reglas.noNegativo(precio, "precio");
        if (nombre.length() > 160 || descripcion.length() > 500 || precio >= 1_000_000_000_0000d) {
            throw new IllegalArgumentException("Datos fuera del tamaño admitido por el catálogo");
        }
        if (java.math.BigDecimal.valueOf(precio).stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El precio admite hasta dos decimales");
        }
    }
}
