package ar.com.coto.stock.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI: especificación en /v3/api-docs y Swagger UI en /swagger-ui.html. */
@Configuration(proxyBeanMethods = false)
public class ConfiguracionOpenApi {
    @Bean OpenAPI documentacionApi() {
        return new OpenAPI().info(new Info()
                .title("COTO · Gestión de Stock")
                .version("0.3.0")
                .description("""
                        API del backoffice de inventario de sucursal: existencias, recepción de pedidos, compras y reposición, \
                        promociones y reservas de Coto Digital.

                        Los errores se devuelven como `application/problem+json` (RFC 9457) con `codigo`, `detail` y, \
                        en validaciones, `errores` por campo. El disponible se calcula como físico − exhibición − reservado; \
                        los combos no tienen contadores propios."""));
    }
}
