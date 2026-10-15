package com.app.order.service;

import com.app.order.dto.request.OrderSearchRequest;
import com.app.order.dto.response.OrderPageResponse;

public interface OrderQueryService {

    OrderPageResponse list(Long userId, boolean administrator, OrderSearchRequest request);
}
