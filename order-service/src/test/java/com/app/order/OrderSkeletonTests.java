package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.order.entity.Inventory;
import com.app.order.entity.OrderStatus;
import com.app.order.entity.PurchaseOrder;
import com.app.order.entity.SalesChannel;
import com.app.order.repository.InventoryRepository;
import com.app.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderSkeletonTests {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private InventoryRepository inventory;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    void savesAggregateWithProductSnapshot() {
        String requestKey = UUID.randomUUID().toString();
        PurchaseOrder order = new PurchaseOrder(1L, requestKey, SalesChannel.WEB);
        order.addItem(1L, "SKU", "Snapshot", new BigDecimal("12.50"), 2);
        orders.saveAndFlush(order);
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(new BigDecimal("25.00"), order.getTotalAmount());
        assertEquals(order.getId(), orders.findByUserIdAndIdempotencyKey(1L, requestKey).orElseThrow().getId());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM order_items WHERE order_id = ?", Integer.class, order.getId()));
    }

    @Test
    void idempotencyKeyIsUniquePerUser() {
        String requestKey = UUID.randomUUID().toString();
        orders.saveAndFlush(new PurchaseOrder(2L, requestKey, SalesChannel.POS));
        assertThrows(DataIntegrityViolationException.class, () ->
                orders.saveAndFlush(new PurchaseOrder(2L, requestKey, SalesChannel.WEB)));
        orders.saveAndFlush(new PurchaseOrder(3L, requestKey, SalesChannel.WEB));
    }

    @Test
    void databaseRejectsNegativeInventory() {
        inventory.saveAndFlush(new Inventory(100L, 10));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE inventory SET available = -1 WHERE product_id = 100"));
        assertEquals(10, inventory.findById(100L).orElseThrow().getAvailable());
    }

    @Test
    void aggregateRejectsInvalidItemQuantity() {
        PurchaseOrder order = new PurchaseOrder(4L, UUID.randomUUID().toString(), SalesChannel.WEB);
        assertThrows(IllegalArgumentException.class, () ->
                order.addItem(1L, "SKU", "Snapshot", BigDecimal.ONE, 0));
    }

    @Test
    void healthIsPublicAndOrdersRequireJwt() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }
}
