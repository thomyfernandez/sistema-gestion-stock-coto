package ar.com.coto.stock.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class ConfiguracionWeb implements WebMvcConfigurer {
    private final List<String> origenes;
    public ConfiguracionWeb(@Value("${coto.web.origenes}") List<String> origenes) { this.origenes = List.copyOf(origenes); }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origenes.toArray(String[]::new))
                .allowedMethods("GET", "HEAD", "POST", "PUT", "PATCH", "OPTIONS").allowedHeaders("Content-Type").maxAge(3600);
    }
}
