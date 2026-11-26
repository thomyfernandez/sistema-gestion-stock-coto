package ar.com.coto.stock.presentation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import javax.sql.DataSource;
import java.time.*;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Temporadas por HTTP, con un reloj de sucursal que la prueba mueve de fecha. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "coto.integraciones.automaticas=false",
        "spring.datasource.url=jdbc:h2:mem:temporadastest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000"
})
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.security.test.context.support.WithMockUser(username = "compras.demo", roles = {"COMPRAS"})
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TemporadasApiTest {
    static final ZoneId SUCURSAL = ZoneId.of("America/Argentina/Buenos_Aires");

    /** Reloj de la sucursal que cada prueba ubica en el día que necesita. */
    static final class RelojAjustable extends Clock {
        private volatile Instant ahora = Instant.now();
        void el(int dia, int mes, int anio) { ahora = LocalDate.of(anio, mes, dia).atTime(10, 0).atZone(SUCURSAL).toInstant(); }
        @Override public ZoneId getZone() { return SUCURSAL; }
        @Override public Clock withZone(ZoneId zona) { return Clock.fixed(ahora, zona); }
        @Override public Instant instant() { return ahora; }
    }
    @TestConfiguration static class Reloj {
        @Bean @Primary RelojAjustable relojDePrueba() { return new RelojAjustable(); }
    }

    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired RelojAjustable reloj;

    @BeforeEach void reiniciar() {
        for (String tabla : List.of("usuario", "imagen_producto", "configuracion_avisos", "evento_integracion", "promocion", "temporada_reposicion", "politica_reposicion",
                "auditoria_stock", "pedido_reposicion", "movimiento_stock", "linea_combo", "stock", "producto", "destinatario_aviso", "categoria", "proveedor",
                "bloqueo_operacion")) jdbc.update("DELETE FROM " + tabla);
        new ResourceDatabasePopulator(new ClassPathResource("data.sql")).execute(dataSource);
        reloj.el(20, 12, 2026);
    }
    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido, String cuerpo) throws Exception {
        return mvc.perform(pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
    private String temporada(String nombre, String alcance, int desdeDia, int desdeMes, int hastaDia, int hastaMes, String factor) {
        return """
            {"nombre":"%s",%s,"desdeDia":%d,"desdeMes":%d,"hastaDia":%d,"hastaMes":%d,"factor":%s}
            """.formatted(nombre, alcance, desdeDia, desdeMes, hastaDia, hastaMes, factor);
    }
    private int pedidoEnviado(int stockId) throws Exception {
        String cuerpo = mvc.perform(get("/api/pedidos?stockId=" + stockId + "&estado=ENVIADO")).andReturn().getResponse().getContentAsString();
        List<Integer> cantidades = JsonPath.read(cuerpo, "$[*].cantidad");
        return cantidades.isEmpty() ? 0 : cantidades.getLast();
    }

    @Test void listaIndicaLaVigenciaSegunElRelojDeLaSucursal() throws Exception {
        mvc.perform(get("/api/temporadas")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Navidad y Reyes")).andExpect(jsonPath("$[0].stockIds", contains(5)))
                .andExpect(jsonPath("$[0].desdeDia").value(15)).andExpect(jsonPath("$[0].desdeMes").value(11))
                .andExpect(jsonPath("$[0].hastaDia").value(6)).andExpect(jsonPath("$[0].hastaMes").value(1))
                .andExpect(jsonPath("$[0].factor").value(1.5)).andExpect(jsonPath("$[0].cruzaElAnio").value(true))
                .andExpect(jsonPath("$[0].vigente").value(true));
        reloj.el(10, 1, 2027);
        mvc.perform(get("/api/temporadas")).andExpect(jsonPath("$[0].vigente").value(false));
    }

    @Test void laEvaluacionUsaElFactorSoloDentroDeLaTemporada() throws Exception {
        // Pan dulce: objetivo base 40, disponible 30.
        mvc.perform(post("/api/reposicion/evaluaciones")).andExpect(status().isOk());
        Assertions.assertEquals(30, pedidoEnviado(5)); // ceil(40 × 1,5) − 30.
        jdbc.update("DELETE FROM evento_integracion WHERE pedido_id IS NOT NULL");
        jdbc.update("DELETE FROM pedido_reposicion WHERE stock_id = 5");
        reloj.el(10, 1, 2027);
        mvc.perform(post("/api/reposicion/evaluaciones")).andExpect(status().isOk());
        Assertions.assertEquals(10, pedidoEnviado(5)); // 40 − 30, sin temporada.
    }

    @Test void factorVigenteInformaOrigenArticuloCategoriaOSinTemporada() throws Exception {
        mvc.perform(get("/api/productos/5/factor-estacional")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value("2026-12-20")).andExpect(jsonPath("$.factor").value(1.5))
                .andExpect(jsonPath("$.origen").value("ARTICULO")).andExpect(jsonPath("$.temporada").value("Navidad y Reyes"));
        jdbc.update("UPDATE producto SET categoria_id = 1 WHERE id = 5");
        json(post("/api/temporadas"), temporada("Invierno almacén", "\"categoriaId\":1", 1, 6, 31, 8, "1.2")).andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/temporadas/"))).andExpect(jsonPath("$.vigente").value(false));
        reloj.el(9, 7, 2026);
        mvc.perform(get("/api/productos/5/factor-estacional")).andExpect(jsonPath("$.origen").value("CATEGORIA"))
                .andExpect(jsonPath("$.factor").value(1.2)).andExpect(jsonPath("$.temporada").value("Invierno almacén"));
        reloj.el(10, 3, 2026);
        mvc.perform(get("/api/productos/5/factor-estacional")).andExpect(jsonPath("$.origen").value("SIN_TEMPORADA"))
                .andExpect(jsonPath("$.factor").value(1)).andExpect(jsonPath("$.temporadaId").value(nullValue()));
        mvc.perform(get("/api/productos/999/factor-estacional")).andExpect(status().isNotFound());
        mvc.perform(get("/api/productos/6/factor-estacional")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("artículos simples")));
    }

    @Test void altaEdicionYBaja() throws Exception {
        String cuerpo = json(post("/api/temporadas"), temporada("Día del padre", "\"stockIds\":[1]", 1, 6, 20, 6, "1.3"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cruzaElAnio").value(false)).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        json(put("/api/temporadas/" + id), temporada("Día del padre", "\"stockIds\":[1]", 1, 6, 21, 6, "1.25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hastaDia").value(21)).andExpect(jsonPath("$.factor").value(1.25));
        json(put("/api/temporadas/" + id), temporada("Mover", "\"stockIds\":[2]", 1, 6, 21, 6, "1.25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockIds", contains(2)));
        mvc.perform(delete("/api/temporadas/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/temporadas/" + id)).andExpect(status().isNotFound());
        json(put("/api/temporadas/" + id), temporada("Ya no existe", "\"stockIds\":[1]", 1, 6, 21, 6, "1.25")).andExpect(status().isNotFound());
        mvc.perform(get("/api/temporadas")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test void superposicionDelMismoAlcanceDevuelveConflicto() throws Exception {
        json(post("/api/temporadas"), temporada("Verano", "\"stockIds\":[5]", 1, 1, 28, 2, "1.2"))
                .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("TEMPORADA_SUPERPUESTA"))
                .andExpect(jsonPath("$.detail").value(containsString("«Navidad y Reyes» (15/11 al 06/01) en Pan dulce COTO 500 g")));
        // Desde el 07/01 ya no se pisa, y otra categoría con las mismas fechas es otro alcance.
        json(post("/api/temporadas"), temporada("Verano", "\"stockIds\":[5]", 7, 1, 28, 2, "1.2")).andExpect(status().isCreated());
        json(post("/api/temporadas"), temporada("Fiestas almacén", "\"categoriaId\":1", 15, 11, 6, 1, "1.2")).andExpect(status().isCreated());
        // Editarla sobre sí misma no es superposición.
        long id = jdbc.queryForObject("SELECT id FROM temporada_reposicion WHERE categoria_id IS NULL AND desde_mes = 11", Long.class);
        json(put("/api/temporadas/" + id), temporada("Navidad y Reyes", "\"stockIds\":[5]", 10, 11, 6, 1, "1.6")).andExpect(status().isOk());
    }

    @Test void unaTemporadaParaVariosArticulos() throws Exception {
        String cuerpo = json(post("/api/temporadas"), temporada("Asado", "\"stockIds\":[2,1,2]", 1, 12, 31, 12, "1.3"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.stockIds", contains(1, 2))).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        mvc.perform(get("/api/productos/1/factor-estacional")).andExpect(jsonPath("$.origen").value("ARTICULO")).andExpect(jsonPath("$.temporada").value("Asado"));
        mvc.perform(get("/api/productos/2/factor-estacional")).andExpect(jsonPath("$.factor").value(1.3));
        mvc.perform(get("/api/productos/3/factor-estacional")).andExpect(jsonPath("$.origen").value("SIN_TEMPORADA"));
        // Comparte el aceite con "Asado" en diciembre: choca y nombra el artículo en común.
        json(post("/api/temporadas"), temporada("Fiestas", "\"stockIds\":[3,2]", 20, 12, 6, 1, "1.5"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(containsString("«Asado» (01/12 al 31/12) en Aceite de girasol COTO 900 ml")));
        // Sin artículos en común, las mismas fechas no chocan.
        json(post("/api/temporadas"), temporada("Fiestas", "\"stockIds\":[3,4]", 20, 12, 6, 1, "1.5")).andExpect(status().isCreated());
        // Quitar un artículo de la temporada lo devuelve a factor 1.
        json(put("/api/temporadas/" + id), temporada("Asado", "\"stockIds\":[1]", 1, 12, 31, 12, "1.3")).andExpect(status().isOk());
        mvc.perform(get("/api/productos/2/factor-estacional")).andExpect(jsonPath("$.origen").value("SIN_TEMPORADA"));
        Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM temporada_articulo WHERE temporada_id = ?", Integer.class, id));
        json(post("/api/temporadas"), temporada("Con combo", "\"stockIds\":[1,6]", 1, 3, 2, 3, "1.1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("artículos simples")));
    }

    @Test void validacionesDevuelvenProblemJson() throws Exception {
        json(post("/api/temporadas"), temporada("Abril", "\"stockIds\":[1]", 31, 4, 30, 5, "1.2"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("REGLA_INVALIDA"))
                .andExpect(jsonPath("$.detail").value("La fecha de inicio 31/04 no existe"));
        json(post("/api/temporadas"), temporada("Cero", "\"stockIds\":[1]", 1, 4, 30, 5, "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.errores.factor").exists());
        json(post("/api/temporadas"), temporada("Excesivo", "\"stockIds\":[1]", 1, 4, 30, 5, "10.5")).andExpect(status().isBadRequest());
        json(post("/api/temporadas"), temporada("Mes 13", "\"stockIds\":[1]", 1, 13, 30, 5, "1.1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.desdeMes").exists());
        json(post("/api/temporadas"), temporada("Ambos", "\"stockIds\":[1],\"categoriaId\":1", 1, 4, 30, 5, "1.1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("uno o más artículos o una categoría")));
        json(post("/api/temporadas"), temporada("Ninguno", "\"stockIds\":[]", 1, 4, 30, 5, "1.1")).andExpect(status().isBadRequest());
        json(post("/api/temporadas"), temporada("Combo", "\"stockIds\":[6]", 1, 4, 30, 5, "1.1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("artículos simples")));
        json(post("/api/temporadas"), temporada("Sin artículo", "\"stockIds\":[999]", 1, 4, 30, 5, "1.1")).andExpect(status().isNotFound());
        json(post("/api/temporadas"), temporada("Sin categoría", "\"categoriaId\":999", 1, 4, 30, 5, "1.1")).andExpect(status().isNotFound());
        json(post("/api/temporadas"), "{\"nombre\":\"Sin fechas\",\"stockIds\":[1],\"factor\":1.2}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.desdeDia").exists());
        mvc.perform(get("/api/temporadas")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test void laPoliticaYaNoExponeFactorPeroAceptaClientesAnteriores() throws Exception {
        mvc.perform(get("/api/productos/5/politica-reposicion")).andExpect(jsonPath("$.tipo").value("ESTACIONAL"))
                .andExpect(jsonPath("$.stockObjetivo").value(40)).andExpect(jsonPath("$.factorEstacional").doesNotExist());
        json(put("/api/productos/5/politica-reposicion"), """
                {"tipo":"ESTACIONAL","puntoPedido":0,"stockObjetivo":50,"demandaDiaria":0,"diasCobertura":1,
                 "factorEstacional":3,"umbralAlerta":10}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.stockObjetivo").value(50));
        json(put("/api/productos/5/politica-reposicion"), """
                {"tipo":"ESTACIONAL","puntoPedido":0,"stockObjetivo":300000000,"demandaDiaria":0,"diasCobertura":1,"umbralAlerta":10}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("CANTIDAD_FUERA_RANGO"));
    }
}
