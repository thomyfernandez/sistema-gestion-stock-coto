package ar.com.coto.stock.domain.model;

import java.math.BigDecimal;

/** Identidad y precio de lista. La disponibilidad pertenece exclusivamente a Stock. */
public final class Producto {
    private final int id;
    private final String nombre;
    private final String descripcion;
    private final BigDecimal precio;
    private final String imagenVersion;
    private final Integer categoriaId;

    public Producto(int id, String nombre, String descripcion, BigDecimal precio) {
        this(id, nombre, descripcion, precio, null);
    }
    public Producto(int id, String nombre, String descripcion, BigDecimal precio, String imagenVersion) {
        this(id, nombre, descripcion, precio, imagenVersion, null);
    }
    public Producto(int id, String nombre, String descripcion, BigDecimal precio, String imagenVersion, Integer categoriaId) {
        this.id = Reglas.positivo(id, "id");
        if (categoriaId != null) Reglas.positivo(categoriaId, "categoriaId");
        this.categoriaId = categoriaId;
        this.nombre = Reglas.texto(nombre, "nombre");
        this.descripcion = Reglas.texto(descripcion, "descripcion");
        this.precio = Reglas.noNegativo(precio, "precio");
        this.imagenVersion = imagenVersion;
    }

    public Integer getCategoriaId() { return categoriaId; }
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPrecio() { return precio; }
    public String getImagenVersion() { return imagenVersion; }
}
