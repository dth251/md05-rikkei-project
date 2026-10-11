package com.app.order.service;

import com.app.order.dto.request.StockRequest;
import com.app.order.dto.response.InventoryResponse;
import java.util.Optional;

public interface StockReceiptTransactions {

    Optional<InventoryResponse> replay(StockRequest request);

    InventoryResponse receive(StockRequest request);
}
