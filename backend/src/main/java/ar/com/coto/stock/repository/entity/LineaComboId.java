package ar.com.coto.stock.repository.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class LineaComboId implements Serializable {
    public int comboId;
    public int componenteId;
    public LineaComboId() { }
    public LineaComboId(int comboId, int componenteId) { this.comboId = comboId; this.componenteId = componenteId; }
    @Override public boolean equals(Object o) {
        return o instanceof LineaComboId otro && comboId == otro.comboId && componenteId == otro.componenteId;
    }
    @Override public int hashCode() { return Objects.hash(comboId, componenteId); }
}
