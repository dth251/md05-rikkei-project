package com.app.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest(properties = "JWT_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class GatewayRouteTests {

    @Autowired
    private RouteDefinitionLocator routes;

    @Test
    void routesUseIndependentBackendsAndPreserveControllerPrefixes() {
        var definitions = routes.getRouteDefinitions().collectList().block();
        assertEquals(List.of("auth-login", "auth-session", "auth-service", "product-service", "order-service", "inventory-service", "payment-service"),
                definitions.stream().map(route -> route.getId()).toList());
        definitions.forEach(route -> {
            assertEquals("http", route.getUri().getScheme());
            assertFalse(route.getFilters().stream()
                    .anyMatch(filter -> filter.getName().equals("StripPrefix")));
        });
    }
}
