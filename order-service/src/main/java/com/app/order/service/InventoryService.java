package com.app.order.service;

import com.app.order.dto.request.StockRequest;
import com.app.order.dto.response.InventoryResponse;

public interface InventoryService {

    InventoryResponse get(Long productId);

    InventoryResponse receive(StockRequest request);
}
