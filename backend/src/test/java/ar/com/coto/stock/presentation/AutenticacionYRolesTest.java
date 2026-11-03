package ar.com.coto.stock.presentation;

import ar.com.coto.stock.config.JwtTokenService;
import ar.com.coto.stock.dto.Solicitudes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AutenticacionYRolesTest {
    @Autowired MockMvc mvc;
    ObjectMapper mapper = new ObjectMapper();
    @Autowired JwtTokenService jwtTokenService;

        @Test
    @DisplayName("Login exitoso con usuario demo devuelve token JWT y datos de rol")
    void loginExitoso() throws Exception {
        var login = new Solicitudes.Login("supervisor.demo", "coto123");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.username", is("supervisor.demo")))
                .andExpect(jsonPath("$.rol", is("SUPERVISOR")))
                .andExpect(jsonPath("$.nombreCompleto", is("Supervisor de Sucursal")));
    }

    @Test
    @DisplayName("Login con contraseña incorrecta devuelve 401 Unauthorized")
    void loginInvalido() throws Exception {
        var login = new Solicitudes.Login("supervisor.demo", "password_invalido");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint protegido sin token devuelve 401 Problem Details")
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.codigo", is("NO_AUTENTICADO")));
    }

    @Test
    @DisplayName("Repositor intentando cancelar pedido de Compras recibe 403 Forbidden")
    void repositorNoPuedeCancelarPedido() throws Exception {
        String token = jwtTokenService.generarToken("repositor.demo", "REPOSITOR", "Repositor");

        mvc.perform(post("/api/pedidos/1/cancelacion")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.codigo", is("ACCESO_DENEGADO")));
    }

    @Test
    @DisplayName("Recepción puede ingresar mercadería con su rol")
    void recepcionPuedeIngresarMercaderia() throws Exception {
        String token = jwtTokenService.generarToken("recepcion.demo", "RECEPCION", "Recepción");
        String json = """
                {"stockId":1,"cantidad":5,"motivo":"Remito ingreso","usuario":"recepcion.demo"}
                """;

        mvc.perform(post("/api/recepcion/ingresos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()));
    }
}

