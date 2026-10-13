package com.app.order.mapper;

import com.app.order.dto.response.OrderReturnResponse;
import com.app.order.entity.OrderReturn;
import org.springframework.stereotype.Component;

@Component
public class OrderReturnMapper {

    public OrderReturnResponse toResponse(OrderReturn record) {
        return new OrderReturnResponse(record.getOrderId(), record.getUserId(), record.getAmount(), record.getStatus(),
                record.getReason(), record.getRequestedAt(), record.getDecidedBy(), record.getDecidedAt(),
                record.getDecisionNote(), record.getReceivedBy(), record.getReceivedAt(), record.getWarehouseReceipt(),
                record.getRestock(), record.getPaymentId(), record.getRefundedAt());
    }
}
