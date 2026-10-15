package ar.com.coto.stock.domain.model;

/** Identidad y precio de lista. La disponibilidad pertenece exclusivamente a Stock. */
public final class Producto {
    private final int id;
    private final String nombre;
    private final String descripcion;
    private final double precio;

    public Producto(int id, String nombre, String descripcion, double precio) {
        this.id = Reglas.positivo(id, "id");
        this.nombre = Reglas.texto(nombre, "nombre");
        this.descripcion = Reglas.texto(descripcion, "descripcion");
        this.precio = Reglas.noNegativo(precio, "precio");
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public double getPrecio() { return precio; }
}
