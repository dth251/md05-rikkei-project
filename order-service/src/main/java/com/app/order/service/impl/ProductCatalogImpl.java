package com.app.order.service.impl;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.response.ProductSnapshot;
import com.app.order.service.ProductCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class ProductCatalogImpl implements ProductCatalog {

    private final RestClient client;

    public ProductCatalogImpl(@Value("${product.base-url}") String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public ProductSnapshot get(Long productId) {
        try {
            JsonNode body = client.get().uri("/api/products/{id}", productId)
                    .retrieve().body(JsonNode.class);
            JsonNode data = body == null ? null : body.path("data");
            if (data == null || !data.path("active").asBoolean() || data.path("id").asLong() != productId
                    || !data.path("unitPrice").isNumber()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable");
            }
            return new ProductSnapshot(productId, data.path("sku").asText(),
                    data.path("name").asText(), data.path("unitPrice").decimalValue());
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable");
        }
    }
}
