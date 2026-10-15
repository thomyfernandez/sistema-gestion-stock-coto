package ar.com.coto.stock.adapter;

/** Especialización explícita del Adapter para el proveedor SMS. */
public final class AdaptadorProveedorSMS extends AdaptadorProveedor {
    public AdaptadorProveedorSMS(ApiProveedorExterno api) { super(api, "SMS"); }
}
