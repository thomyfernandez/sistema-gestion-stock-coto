package ar.com.coto.stock.application;

import ar.com.coto.stock.domain.model.*;
import ar.com.coto.stock.domain.port.*;
import ar.com.coto.stock.service.ServicioDestinatarios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ServicioDestinatariosTest {

    private MemoriaDestinatarios repoDestinatarios;
    private MemoriaStock repoStock;
    private MemoriaCategorias repoCategorias;
    private ServicioDestinatarios servicio;

    @BeforeEach
    void setup() {
        repoDestinatarios = new MemoriaDestinatarios();
        repoStock = new MemoriaStock();
        repoCategorias = new MemoriaCategorias();
        UnidadDeTrabajo udt = new UnidadDeTrabajo() {
            public <T> T ejecutar(Supplier<T> s) { return s.get(); }
            public <T> T consultar(Supplier<T> s) { return s.get(); }
            public <T> T ejecutarNueva(Supplier<T> s) { return s.get(); }
            public void despuesDeConfirmar(Runnable r) { r.run(); }
        };
        servicio = new ServicioDestinatarios(repoDestinatarios, repoStock, repoCategorias, udt);

        // Categorías iniciales
        repoCategorias.guardar(new Categoria(1, "Almacén", null, null));
        repoCategorias.guardar(new Categoria(2, "Frescos", null, null));
        repoCategorias.guardar(new Categoria(3, "Bebidas", null, null));

        // Productos de muestra
        repoStock.agregar(new StockSimple(new Producto(1, "Leche", "1L", 1500, null, 2), 20, 0, 0)); // cat 2 (Frescos)
        repoStock.agregar(new StockSimple(new Producto(2, "Arroz", "1kg", 1200, null, 1), 30, 0, 0)); // cat 1 (Almacén)
        repoStock.agregar(new StockSimple(new Producto(3, "Suelto", "Granel", 800, null, null), 15, 0, 0)); // sin categoría
    }

    @Test
    @DisplayName("CA5 — Resuelve correos específicos de la categoría si posee destinatarios configurados")
    void resolverDestinatariosUsaListaDeCategoria() {
        repoDestinatarios.guardarGenerales(List.of("general1@coto.com.ar", "general2@coto.com.ar"));
        repoDestinatarios.guardarPorCategoria(2, List.of("frescos1@coto.com.ar", "frescos2@coto.com.ar"));

        List<String> resueltos = servicio.resolverDestinatarios(1); // Leche en Frescos (cat 2)

        assertEquals(List.of("frescos1@coto.com.ar", "frescos2@coto.com.ar"), resueltos);
    }

    @Test
    @DisplayName("CA6 — Resuelve lista general si la categoría del artículo no tiene correos cargados")
    void resolverDestinatariosUsaListaGeneralSiCategoriaVacia() {
        repoDestinatarios.guardarGenerales(List.of("general1@coto.com.ar", "general2@coto.com.ar"));
        // Categoría 1 (Almacén) no tiene destinatarios configurados

        List<String> resueltos = servicio.resolverDestinatarios(2); // Arroz en Almacén (cat 1)

        assertEquals(List.of("general1@coto.com.ar", "general2@coto.com.ar"), resueltos);
    }

    @Test
    @DisplayName("CA6 — Resuelve lista general si el artículo no pertenece a ninguna categoría")
    void resolverDestinatariosUsaListaGeneralSiArticuloSinCategoria() {
        repoDestinatarios.guardarGenerales(List.of("general1@coto.com.ar"));

        List<String> resueltos = servicio.resolverDestinatarios(3); // Suelto (sin cat)

        assertEquals(List.of("general1@coto.com.ar"), resueltos);
    }

    @Test
    @DisplayName("Resuelve lista vacía si ni la categoría ni la sucursal tienen destinatarios")
    void resolverDestinatariosDevuelveListaVaciaSiTodoVacio() {
        List<String> resueltos = servicio.resolverDestinatarios(1);
        assertTrue(resueltos.isEmpty());
    }

    @Test
    @DisplayName("CA1 — Guarda lista general validando formato y persistiendo sin duplicados")
    void guardarGeneralesExitoso() {
        servicio.guardarGenerales(List.of("compras@coto.com.ar", "STOCK@COTO.COM.AR"));

        List<String> generales = servicio.listarGenerales();
        assertEquals(2, generales.size());
        assertTrue(generales.contains("compras@coto.com.ar"));
        assertTrue(generales.contains("stock@coto.com.ar"));
    }

    @Test
    @DisplayName("CA1 — Rechaza lista general si algún correo tiene formato inválido")
    void guardarGeneralesRechazaFormatoInvalido() {
        assertThrows(IllegalArgumentException.class, () ->
                servicio.guardarGenerales(List.of("valido@coto.com.ar", "invalido-sin-arroba")));
    }

    @Test
    @DisplayName("CA2 — Guarda lista por categoría validando unicidad, formato y existencia de categoría")
    void guardarPorCategoriaExitoso() {
        servicio.guardarPorCategoria(1, List.of("almacen@coto.com.ar"));

        Map<Integer, List<String>> porCat = servicio.listarPorCategoria();
        assertEquals(List.of("almacen@coto.com.ar"), porCat.get(1));
    }

    @Test
    @DisplayName("CA2 — Falla al intentar guardar destinatarios en una categoría inexistente")
    void guardarPorCategoriaFallaSiCategoriaInexistente() {
        assertThrows(EntidadNoEncontradaException.class, () ->
                servicio.guardarPorCategoria(999, List.of("test@coto.com.ar")));
    }

    @Test
    @DisplayName("CA2 — Rechaza categoriaId menor o igual a cero")
    void guardarPorCategoriaRechazaIdInvalido() {
        assertThrows(IllegalArgumentException.class, () ->
                servicio.guardarPorCategoria(0, List.of("test@coto.com.ar")));
        assertThrows(IllegalArgumentException.class, () ->
                servicio.guardarPorCategoria(-1, List.of("test@coto.com.ar")));
    }

    @Test
    @DisplayName("Guardar lista vacía en categoría limpia sus registros para volver a heredar de la general")
    void guardarPorCategoriaListaVaciaLimpiaYPermiteHeredar() {
        servicio.guardarGenerales(List.of("general@coto.com.ar"));
        servicio.guardarPorCategoria(2, List.of("propio@coto.com.ar"));
        assertEquals(List.of("propio@coto.com.ar"), servicio.resolverDestinatarios(1));

        servicio.guardarPorCategoria(2, List.of());
        assertEquals(List.of("general@coto.com.ar"), servicio.resolverDestinatarios(1));
    }

    // --- Implementaciones simuladas de puertos en memoria ---

    private static class MemoriaDestinatarios implements RepositorioDestinatarios {
        private final List<DestinatarioAviso> lista = new ArrayList<>();
        private long idSeq = 1;

        public List<DestinatarioAviso> listarGenerales() {
            return lista.stream().filter(DestinatarioAviso::esGeneral).toList();
        }

        public List<DestinatarioAviso> listarPorCategoria(int categoriaId) {
            return lista.stream().filter(d -> Objects.equals(d.categoriaId(), categoriaId)).toList();
        }

        public List<DestinatarioAviso> listarTodos() {
            return List.copyOf(lista);
        }

        public void guardarGenerales(List<String> emails) {
            lista.removeIf(DestinatarioAviso::esGeneral);
            if (emails != null) {
                Set<String> normalizados = new LinkedHashSet<>();
                for (String e : emails) {
                    if (e != null && !e.isBlank()) normalizados.add(e.trim().toLowerCase());
                }
                for (String email : normalizados) {
                    lista.add(new DestinatarioAviso(idSeq++, null, email));
                }
            }
        }

        public void guardarPorCategoria(int categoriaId, List<String> emails) {
            lista.removeIf(d -> Objects.equals(d.categoriaId(), categoriaId));
            if (emails != null) {
                Set<String> normalizados = new LinkedHashSet<>();
                for (String e : emails) {
                    if (e != null && !e.isBlank()) normalizados.add(e.trim().toLowerCase());
                }
                for (String email : normalizados) {
                    lista.add(new DestinatarioAviso(idSeq++, categoriaId, email));
                }
            }
        }
    }

    private static class MemoriaStock implements RepositorioStock {
        private final Map<Integer, Stock> mapa = new HashMap<>();

        void agregar(Stock stock) { mapa.put(stock.getId(), stock); }

        public Map<Integer, Stock> cargarInventario() { return Map.copyOf(mapa); }
        public void guardarDisponibilidad(int stockId, boolean disponible) {}
        public void bloquearInventario() {}
        public int actualizarContadores(StockSimple stock, long versionEsperada) { return 1; }
        public int actualizarContadoresCombo(ComboStock combo, long versionEsperada) { return 1; }
    }

    private static class MemoriaCategorias implements RepositorioCategorias {
        private final Map<Integer, Categoria> mapa = new HashMap<>();

        public List<Categoria> listar() { return new ArrayList<>(mapa.values()); }
        public Optional<Categoria> obtener(int id) { return Optional.ofNullable(mapa.get(id)); }
        public Categoria guardar(Categoria cat) {
            mapa.put(cat.id(), cat);
            return cat;
        }
        public boolean existeNombre(String nombre, Integer excluirId) { return false; }
    }
}
