package com.app.order.service.impl;

import com.app.order.dto.event.CommerceEvent;
import com.app.order.entity.OrderStatus;
import com.app.order.repository.OrderRepository;
import com.app.order.repository.StockRepository;
import com.app.order.service.OutboxService;
import com.app.order.service.ReservationExpiryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationExpiryServiceImpl implements ReservationExpiryService {

    private final OrderRepository orders;
    private final StockRepository stocks;
    private final OutboxService outbox;
    private final JdbcTemplate jdbc;

    public ReservationExpiryServiceImpl(
        OrderRepository orders,
        StockRepository stocks,
        OutboxService outbox,
        JdbcTemplate jdbc
    ) {
        this.orders = orders;
        this.stocks = stocks;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> candidates(Instant cutoff) {
        return jdbc.queryForList("SELECT id FROM orders WHERE status='PENDING_PAYMENT' "
                + "AND created_at<=? ORDER BY created_at,id LIMIT 50", Long.class, Timestamp.from(cutoff));
    }

    @Override
    @Transactional
    public boolean expire(Long orderId, Instant cutoff) {
        var order = orders.findLockedById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getCreatedAt().isAfter(cutoff)) {
            return false;
        }
        for (var item : order.getItems().stream().sorted(Comparator.comparing(item -> item.getProductId())).toList()) {
            if (stocks.finalizeReservation(item.getProductId(), item.getQuantity(), false) != 1) {
                throw new IllegalStateException("Reserved stock inconsistent; expiration rolled back");
            }
        }
        order.expire();
        outbox.append(new CommerceEvent(UUID.randomUUID().toString(), "order.expired", order.getId(),
                order.getUserId(), order.getTotalAmount(), null, Instant.now()));
        orders.flush();
        return true;
    }
}
