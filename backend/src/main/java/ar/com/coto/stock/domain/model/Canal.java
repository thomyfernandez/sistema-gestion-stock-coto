package ar.com.coto.stock.domain.model;

/** Canal por el que se entrega un aviso. EMAIL usa la dirección; SMS y WHATSAPP, el teléfono. */
public enum Canal {
    EMAIL, SMS, WHATSAPP;

    public boolean usaEmail() { return this == EMAIL; }
}
