package com.app.order.repository;

import com.app.order.dto.response.InventoryResponse;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockRepository {

    private final JdbcTemplate jdbc;

    public StockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int reserve(
        Long productId,
        int quantity
    ) {
        return jdbc.update("UPDATE inventory SET available = available - ?, reserved = reserved + ?, "
                + "version = version + 1 WHERE product_id = ? AND available >= ? AND reserved <= 2147483647 - ?",
                quantity, quantity, productId, quantity, quantity);
    }

    public int receive(
        Long productId,
        int quantity
    ) {
        return jdbc.update("INSERT INTO inventory (product_id, available, reserved, version) VALUES (?, ?, 0, 0) "
                + "ON CONFLICT (product_id) DO UPDATE SET available = inventory.available + EXCLUDED.available, "
                + "version = inventory.version + 1 WHERE inventory.available <= 2147483647 - inventory.reserved - EXCLUDED.available",
                productId, quantity);
    }

    public int finalizeReservation(
        Long productId,
        int quantity,
        boolean successful
    ) {
        return jdbc.update("UPDATE inventory SET reserved = reserved - ?, available = available + ?, "
                + "version = version + 1 WHERE product_id = ? AND reserved >= ? AND available <= 2147483647 - ?",
                quantity, successful ? 0 : quantity, productId, quantity, successful ? 0 : quantity);
    }

    public Optional<InventoryResponse> find(Long productId) {
        return jdbc.query("SELECT product_id, available, reserved FROM inventory WHERE product_id = ?",
                (result, row) -> new InventoryResponse(result.getLong(1), result.getInt(2), result.getInt(3)),
                productId).stream().findFirst();
    }
}
