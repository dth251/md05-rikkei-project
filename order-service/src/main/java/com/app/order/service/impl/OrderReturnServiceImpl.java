package com.app.order.service.impl;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.event.CommerceEvent;
import com.app.order.dto.request.DecideReturnRequest;
import com.app.order.dto.request.ReceiveReturnRequest;
import com.app.order.dto.request.RequestReturnRequest;
import com.app.order.dto.response.OrderReturnResponse;
import com.app.order.entity.OrderReturn;
import com.app.order.entity.OrderStatus;
import com.app.order.entity.ReturnStatus;
import com.app.order.mapper.OrderReturnMapper;
import com.app.order.repository.EventInbox;
import com.app.order.repository.OrderRepository;
import com.app.order.repository.OrderReturnRepository;
import com.app.order.repository.StockRepository;
import com.app.order.service.OrderReturnService;
import com.app.order.service.OutboxService;
import java.util.Comparator;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderReturnServiceImpl implements OrderReturnService {

    private final OrderRepository orders;
    private final OrderReturnRepository returns;
    private final StockRepository stocks;
    private final OrderReturnMapper mapper;
    private final OutboxService outbox;
    private final EventInbox inbox;

    public OrderReturnServiceImpl(
            OrderRepository orders,
            OrderReturnRepository returns,
            StockRepository stocks,
            OrderReturnMapper mapper,
            OutboxService outbox,
            EventInbox inbox
    ) {
        this.orders = orders;
        this.returns = returns;
        this.stocks = stocks;
        this.mapper = mapper;
        this.outbox = outbox;
        this.inbox = inbox;
    }

    @Override
    @Transactional
    public OrderReturnResponse request(
            Long orderId,
            Long userId,
            boolean administrator,
            RequestReturnRequest request
    ) {
        var order = orders.findLockedById(orderId).orElseThrow(this::notFound);
        if (!administrator && !order.getUserId().equals(userId)) {
            throw notFound();
        }
        String key = request.idempotencyKey().strip();
        String reason = request.reason().strip();
        var existing = returns.findById(orderId);
        if (existing.isPresent()) {
            if (!existing.get().getIdempotencyKey().equals(key) || !existing.get().getReason().equals(reason)) {
                throw conflict("A full-order return already exists with different details");
            }
            return mapper.toResponse(existing.get());
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw conflict("Only completed orders can request a return");
        }
        var record = returns.saveAndFlush(new OrderReturn(order, key, reason));
        return mapper.toResponse(record);
    }

    @Override
    public OrderReturnResponse get(
            Long orderId,
            Long userId,
            boolean administrator
    ) {
        var record = returns.findById(orderId).orElseThrow(this::notFound);
        if (!administrator && !record.getUserId().equals(userId)) {
            throw notFound();
        }
        return mapper.toResponse(record);
    }

    @Override
    @Transactional
    public OrderReturnResponse decide(
            Long orderId,
            Long administratorId,
            DecideReturnRequest request
    ) {
        var record = locked(orderId);
        String note = request.note().strip();
        if (record.getStatus() != ReturnStatus.REQUESTED) {
            boolean approved = record.getStatus() != ReturnStatus.REJECTED;
            if (approved != request.approved() || !note.equals(record.getDecisionNote())
                    || !administratorId.equals(record.getDecidedBy())) {
                throw conflict("Return decision already recorded with different details");
            }
            return mapper.toResponse(record);
        }
        record.decide(request.approved(), note, administratorId);
        returns.flush();
        return mapper.toResponse(record);
    }

    @Override
    @Transactional
    public OrderReturnResponse receive(
            Long orderId,
            Long administratorId,
            ReceiveReturnRequest request
    ) {
        var record = locked(orderId);
        String receipt = request.warehouseReceipt().strip();
        if (record.getReceivedAt() != null) {
            if (!receipt.equals(record.getWarehouseReceipt()) || !request.restock().equals(record.getRestock())
                    || !administratorId.equals(record.getReceivedBy())) {
                throw conflict("Return receipt already recorded with different details");
            }
            return mapper.toResponse(record);
        }
        if (record.getStatus() != ReturnStatus.APPROVED) {
            throw conflict("Only approved returns can receive goods");
        }
        var order = orders.findById(orderId).orElseThrow(this::notFound);
        if (request.restock()) {
            for (var item : order.getItems().stream().sorted(Comparator.comparing(i -> i.getProductId())).toList()) {
                if (stocks.restockReturned(item.getProductId(), item.getQuantity()) != 1) {
                    throw conflict("Returned stock cannot be added; receipt rolled back");
                }
            }
        }
        record.receive(receipt, request.restock(), administratorId);
        outbox.append(new CommerceEvent(UUID.randomUUID().toString(), "order.return-received", record.getOrderId(),
                record.getUserId(), record.getAmount(), order.getCodPaymentId(), record.getReceivedAt()));
        returns.flush();
        return mapper.toResponse(record);
    }

    @Override
    @Transactional
    public void acceptRefund(CommerceEvent event) {
        if (!("payment.return-refund-ready".equals(event.type()) || "payment.return-refunded".equals(event.type()))) {
            throw new IllegalArgumentException("Invalid return refund event type");
        }
        if (!inbox.claim(event.eventId(), event.type())) {
            return;
        }
        var order = orders.findLockedById(event.orderId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown order in return refund event"));
        var record = returns.findById(event.orderId()).orElseThrow(() ->
                new IllegalArgumentException("Unknown return in refund event"));
        if (!record.getUserId().equals(event.userId()) || record.getAmount().compareTo(event.amount()) != 0) {
            throw new IllegalArgumentException("Refund event does not match returned order");
        }
        if (order.getCodPaymentId() != null && !order.getCodPaymentId().equals(event.paymentId())) {
            throw new IllegalArgumentException("COD return refund payment identity mismatch");
        }
        record.acknowledgeRefund(event.paymentId(), "payment.return-refunded".equals(event.type()), event.occurredAt());
        returns.flush();
    }

    private OrderReturn locked(Long orderId) {
        // Use the same parent row lock for request/decision/receipt/refund and fulfillment.
        orders.findLockedById(orderId).orElseThrow(this::notFound);
        return returns.findById(orderId).orElseThrow(this::notFound);
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Order return not found");
    }
}
