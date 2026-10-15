package ar.com.coto.stock.adapter;

import ar.com.coto.stock.domain.port.MonitorSistemasExternos;
import java.util.Map;

public final class MonitorSimulado implements MonitorSistemasExternos {
    private final SistemaERPExterno erp;
    private final ApiProveedorExterno mensajeria;
    private final ApiCotoDigitalExterna digital;
    public MonitorSimulado(SistemaERPExterno erp, ApiProveedorExterno mensajeria, ApiCotoDigitalExterna digital) {
        this.erp = erp; this.mensajeria = mensajeria; this.digital = digital;
    }
    @Override public Resumen consultar() {
        return new Resumen(Map.of(Sistema.ERP, erp.isDisponible(), Sistema.MENSAJERIA, mensajeria.isDisponible(),
                Sistema.COTO_DIGITAL, digital.isDisponible()),
                mensajeria.getMensajes().stream().map(m -> new Mensaje(m.canal(), m.to(), m.body())).toList(),
                erp.getOrdenes().stream().map(p -> new Orden(p.reference(), p.sku(), p.units(), p.date())).toList(),
                digital.getInventario());
    }
    @Override public void establecerDisponibilidad(Sistema sistema, boolean disponible) {
        switch (sistema) {
            case ERP -> erp.setDisponible(disponible);
            case MENSAJERIA -> mensajeria.setDisponible(disponible);
            case COTO_DIGITAL -> digital.setDisponible(disponible);
        }
    }
}
