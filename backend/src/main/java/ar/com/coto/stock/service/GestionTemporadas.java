package ar.com.coto.stock.service;

import ar.com.coto.stock.domain.reposicion.CalendarioTemporadas;
import ar.com.coto.stock.domain.reposicion.Temporada;
import java.time.LocalDate;
import java.util.List;

/** Temporadas de la política de reposición estacional. */
public interface GestionTemporadas {
    /** Temporada con la indicación de si rige en la fecha de la sucursal. */
    record Consulta(Temporada temporada, boolean vigente) { }
    /** Factor que aplica hoy al artículo y la fecha con la que se evaluó. */
    record Vigencia(int stockId, LocalDate fecha, CalendarioTemporadas.FactorVigente factor) { }

    List<Consulta> listar();
    Consulta crear(Temporada temporada);
    Consulta editar(long id, Temporada temporada);
    void eliminar(long id);
    Vigencia factorVigente(int stockId);
}
