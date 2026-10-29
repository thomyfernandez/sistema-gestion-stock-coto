package ar.com.coto.stock.service;
import ar.com.coto.stock.domain.model.Categoria;
import java.util.List;
public interface GestionCategorias {
    List<Categoria> listar();
    Categoria consultar(int id);
    Categoria guardar(Categoria categoria);
}
