package com.app.order.service;

import com.app.order.dto.event.CommerceEvent;
import com.app.order.dto.request.DecideReturnRequest;
import com.app.order.dto.request.ReceiveReturnRequest;
import com.app.order.dto.request.RequestReturnRequest;
import com.app.order.dto.response.OrderReturnResponse;

public interface OrderReturnService {
    OrderReturnResponse request(
            Long orderId,
            Long userId,
            boolean administrator,
            RequestReturnRequest request
    );

    OrderReturnResponse get(
            Long orderId,
            Long userId,
            boolean administrator
    );

    OrderReturnResponse decide(
            Long orderId,
            Long administratorId,
            DecideReturnRequest request
    );

    OrderReturnResponse receive(
            Long orderId,
            Long administratorId,
            ReceiveReturnRequest request
    );

    void acceptRefund(CommerceEvent event);
}
