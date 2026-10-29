package ar.com.coto.stock.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** La especificación OpenAPI cubre la API REST completa y Swagger UI queda disponible. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "coto.integraciones.automaticas=false",
        "spring.datasource.url=jdbc:h2:mem:documentaciontest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DocumentacionApiTest {
    @Autowired MockMvc mvc;

    @Test void especificacionDescribeLaApiConSusEtiquetas() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("COTO · Gestión de Stock"))
                .andExpect(jsonPath("$.paths['/api/productos'].get").exists())
                .andExpect(jsonPath("$.paths['/api/digital/reservas'].post").exists())
                .andExpect(jsonPath("$.paths['/api/pedidos/{id}/recepcion'].post").exists())
                .andExpect(jsonPath("$.paths['/']").doesNotExist())
                .andExpect(jsonPath("$.tags[*].name", hasItems("Productos", "Combos", "Pedidos y reposición", "Operaciones de sucursal y Coto Digital")));
    }

    @Test void swaggerUiDisponibleDesdeElInicio() throws Exception {
        mvc.perform(get("/")).andExpect(jsonPath("$.documentacion").value("/swagger-ui.html"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
