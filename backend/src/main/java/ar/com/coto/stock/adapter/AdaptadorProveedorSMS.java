package ar.com.coto.stock.adapter;

import ar.com.coto.stock.domain.model.Canal;

/** Especialización explícita del Adapter para el proveedor SMS. */
public final class AdaptadorProveedorSMS extends AdaptadorProveedor {
    public AdaptadorProveedorSMS(ApiProveedorExterno api) { super(api, Canal.SMS); }
}
