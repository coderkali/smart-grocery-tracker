package com.smartfinvo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Smart Grocery Tracker API")
                .description("""
                    AI-powered Smart Grocery Tracker — Modular Monolith.

                    **Authentication:**
                    - Login via Google OAuth2: `GET /oauth2/authorization/google`
                    - On success you receive a JWT access token in the response body and a refresh token as an HttpOnly cookie.
                    - Pass the access token as `Authorization: Bearer <token>` on every protected request.
                    - Use `POST /api/v1/auth/refresh` (no header needed — cookie is sent automatically) to rotate tokens.

                    **Modules:**
                    - **Auth** — session management, OAuth2 login, token rotation
                    - **AI** — natural language grocery management, smart suggestions, recipe chat, budget analysis
                    - **Expenses** — create, read, update, delete expense records; filter by date range or category
                    - **Categories** — manage expense categories used to classify spending
                    """)
                .version("v1.0.0")
                .contact(new Contact()
                    .name("SmartFinvo Team")
                    .email("support@smartfinvo.com"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Local Development")))
            .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
            .components(new Components()
                .addSecuritySchemes("BearerAuth", new SecurityScheme()
                    .name("BearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT access token. Obtain it after OAuth2 login. Send as: `Authorization: Bearer <token>`")));
    }
}