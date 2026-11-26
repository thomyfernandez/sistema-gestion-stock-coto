package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.model.Canal;

public interface GestionNotificaciones {
    void setCanal(Canal canal);
    Canal getCanal();
    ar.com.coto.stock.domain.model.ConfiguracionAvisos consultarConfiguracion();
    void configurar(ar.com.coto.stock.domain.model.ConfiguracionAvisos configuracion);
}
