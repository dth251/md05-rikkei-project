package com.app.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI serviceContract() {
        var error = new ObjectSchema().addProperty("success", new BooleanSchema())
                .addProperty("message", new StringSchema()).addProperty("data", new Schema<>())
                .addProperty("errors", new ObjectSchema()).addProperty("timestamp", new StringSchema().format("date-time"));
        return new OpenAPI().info(new Info().title("payment-service").version("1.0"))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSchemas("ApiError", error)
                        .addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .security(List.of(new SecurityRequirement().addList("bearerAuth")));
    }

    @Bean
    OpenApiCustomizer operationSecurityAndResponses() {
        return api -> {
            api.getComponents().addSchemas("ApiError", new ObjectSchema()
                    .addProperty("success", new BooleanSchema()).addProperty("message", new StringSchema())
                    .addProperty("data", new Schema<>()).addProperty("errors", new ObjectSchema())
                    .addProperty("timestamp", new StringSchema().format("date-time")));
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                boolean anonymous = method.name().equals("GET") && Set.of(
                        "/api/payments/vnpay/ipn", "/api/payments/vnpay/return").contains(path);
                boolean admin = path.endsWith("/simulate") || path.endsWith("/vnpay/refund")
                        || path.endsWith("/vnpay/reconcile") || path.endsWith("/cod/collect")
                        || path.endsWith("/simulate-refund")
                        || (path.endsWith("/cod/refund") && method.name().equals("POST"));
                if (anonymous) {
                    operation.setSecurity(new ArrayList<>());
                }
                operation.addExtension("x-required-role", anonymous ? "PUBLIC" : admin ? "ADMIN" : "USER");
                if (path.equals("/api/payments/vnpay/ipn")) {
                    return;
                }
                for (var status : Map.of("400", "Invalid request", "401", "Authentication required",
                        "403", "Access denied", "404", "Not found", "409", "Conflict",
                        "503", "Dependency unavailable").entrySet()) {
                    operation.getResponses().addApiResponse(status.getKey(), new ApiResponse().description(status.getValue())
                            .content(new Content().addMediaType("application/json", new MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
                }
            }));
        };
    }
}
