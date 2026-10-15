package ar.com.coto.stock.service;

public interface GestionNotificaciones {
    enum Canal { EMAIL, SMS, WHATSAPP }
    void setCanal(Canal canal);
    Canal getCanal();
}
