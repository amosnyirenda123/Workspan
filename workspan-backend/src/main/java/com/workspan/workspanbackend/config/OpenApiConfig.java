package com.workspan.workspanbackend.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "sessionCookie", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.COOKIE, paramName = "JSESSIONID")
public class OpenApiConfig {
    @Bean
    OpenAPI workspanOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Workspan API")
                .version("v1")
                .description("API de gestion des espaces, projets et tâches Workspan."));
    }
}
