package com.github.wf.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workflowEngineOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Workflow Engine API")
                        .description("REST API for the Workflow Engine — deploy process definitions, " +
                                "start instances, manage tasks, and monitor execution.")
                        .version("5.1.0")
                        .contact(new Contact()
                                .name("Workflow Engine Team")));
    }
}
