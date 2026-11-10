package com.groundedagri.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI agriMarketOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Grounded Agri-Market Assistant API")
                .version("0.1.0")
                .description("Crop Q&A, image-based symptom intake, source retrieval, and marketplace draft operations."));
    }
}
