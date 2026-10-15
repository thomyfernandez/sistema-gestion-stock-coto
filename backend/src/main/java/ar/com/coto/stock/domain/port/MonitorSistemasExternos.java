package ar.com.coto.stock.domain.port;

import java.util.List;
import java.util.Map;

/** Contrato de inspección de las integraciones simuladas; no expone clases del proveedor. */
public interface MonitorSistemasExternos {
    enum Sistema { ERP, MENSAJERIA, COTO_DIGITAL }
    record Mensaje(String canal, String destino, String texto) { }
    record Orden(String referencia, int productoId, int cantidad, String fecha) { }
    record Resumen(Map<Sistema, Boolean> disponibles, List<Mensaje> mensajes, List<Orden> ordenes,
                   Map<String, String> catalogoDigital) {
        public Resumen {
            disponibles = Map.copyOf(disponibles); mensajes = List.copyOf(mensajes);
            ordenes = List.copyOf(ordenes); catalogoDigital = Map.copyOf(catalogoDigital);
        }
    }
    Resumen consultar();
    void establecerDisponibilidad(Sistema sistema, boolean disponible);
}
