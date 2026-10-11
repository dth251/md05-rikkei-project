package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.request.StockRequest;
import com.app.order.dto.response.InventoryResponse;
import com.app.order.dto.response.ProductSnapshot;
import com.app.order.repository.StockRepository;
import com.app.order.service.InventoryService;
import com.app.order.service.ProductCatalog;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class StockReceiptTests {

    @Autowired
    private InventoryService inventory;

    @MockitoBean
    private StockRepository stocks;

    @MockitoBean
    private ProductCatalog catalog;

    @Test
    void parallelReplayPreservesOriginalResponseAndRejectsChangedPayload() throws Exception {
        when(catalog.get(anyLong())).thenReturn(new ProductSnapshot(71001L, "RECEIPT", "Receipt", BigDecimal.TEN));
        when(stocks.receive(71001L, 7)).thenReturn(1);
        when(stocks.find(71001L)).thenReturn(Optional.of(new InventoryResponse(71001L, 7, 0)));
        StockRequest request = new StockRequest(71001L, 7, UUID.randomUUID().toString());
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<InventoryResponse>> tasks = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                tasks.add(() -> inventory.receive(request));
            }
            for (var result : executor.invokeAll(tasks)) {
                assertEquals(7, result.get().available());
            }
        }
        verify(stocks, times(1)).receive(71001L, 7);
        when(stocks.find(71001L)).thenReturn(Optional.of(new InventoryResponse(71001L, 999, 0)));
        assertEquals(7, inventory.receive(request).available());
        assertThrows(ApiException.class, () -> inventory.receive(new StockRequest(71001L, 8, request.idempotencyKey())));
    }

    @Test
    void failureRollsBackReceiptSoSameKeyCanBeRetried() {
        when(catalog.get(anyLong())).thenReturn(new ProductSnapshot(71002L, "RETRY", "Retry", BigDecimal.TEN));
        StockRequest request = new StockRequest(71002L, 1, UUID.randomUUID().toString());
        when(stocks.receive(71002L, 1)).thenReturn(0);
        assertThrows(ApiException.class, () -> inventory.receive(request));
        when(stocks.receive(71002L, 1)).thenReturn(1);
        when(stocks.find(71002L)).thenReturn(Optional.of(new InventoryResponse(71002L, 1, 0)));
        assertEquals(1, inventory.receive(request).available());
    }
}
