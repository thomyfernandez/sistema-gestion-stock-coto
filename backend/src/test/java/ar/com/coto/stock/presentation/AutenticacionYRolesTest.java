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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @DisplayName("Recepción puede ingresar mercadería con su rol y la auditoría toma el usuario del JWT")
    void recepcionPuedeIngresarMercaderia() throws Exception {
        String token = jwtTokenService.generarToken("recepcion.demo", "RECEPCION", "Operador de Recepción");
        String json = """
                {"stockId":1,"cantidad":5,"motivo":"Remito ingreso"}
                """;

        var resultado = mvc.perform(post("/api/recepcion/ingresos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn();

        mvc.perform(get(resultado.getResponse().getHeader("Location"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario", is("recepcion.demo")));
    }

    @Test
    @DisplayName("Un operador declarado en el cuerpo se rechaza: no se puede suplantar al usuario del JWT")
    void operadorDeclaradoEnElCuerpoSeRechaza() throws Exception {
        String token = jwtTokenService.generarToken("recepcion.demo", "RECEPCION", "Operador de Recepción");

        mvc.perform(post("/api/recepcion/ingresos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockId":1,"cantidad":5,"motivo":"Remito ingreso","usuario":"otro.suplantado"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Perfil autenticado devuelve el nombre completo del JWT")
    void perfilDevuelveNombreCompletoDelJwt() throws Exception {
        String token = jwtTokenService.generarToken("supervisor.demo", "SUPERVISOR", "Supervisor de Sucursal");

        mvc.perform(get("/api/auth/perfil")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("supervisor.demo")))
                .andExpect(jsonPath("$.nombreCompleto", is("Supervisor de Sucursal")))
                .andExpect(jsonPath("$.rol", is("SUPERVISOR")));
    }

    @Test
    @DisplayName("Endpoints de configuración, categorías, promociones y movimientos genéricos validan rol")
    void endpointsRestringidosValidanRol() throws Exception {
        String tokenRepositor = jwtTokenService.generarToken("repositor.demo", "REPOSITOR", "Repositor");
        String tokenCompras = jwtTokenService.generarToken("compras.demo", "COMPRAS", "Compras");

        mvc.perform(delete("/api/productos/1/promociones/1")
                        .header("Authorization", "Bearer " + tokenRepositor))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/productos/1/categoria")
                        .header("Authorization", "Bearer " + tokenRepositor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoriaId\":1}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/movimientos")
                        .header("Authorization", "Bearer " + tokenRepositor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stockId\":1,\"tipo\":\"DEVOLUCION\",\"cantidad\":1,\"motivo\":\"Prueba\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/notificaciones/canal")
                        .header("Authorization", "Bearer " + tokenCompras)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canal\":\"EMAIL\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/integraciones/reintentos")
                        .header("Authorization", "Bearer " + tokenCompras))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CORS habilita origen configurado con Authorization y rechaza orígenes ajenos")
    void corsPermiteFrontendLocalYRechazaOrigenesAjenos() throws Exception {
        mvc.perform(options("/api/productos")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mvc.perform(options("/api/productos")
                        .header("Origin", "https://otro.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}

