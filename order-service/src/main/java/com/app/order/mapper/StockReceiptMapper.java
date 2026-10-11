package com.app.order.mapper;

import com.app.order.dto.response.InventoryResponse;
import com.app.order.entity.StockReceipt;
import org.springframework.stereotype.Component;

@Component
public class StockReceiptMapper {

    public InventoryResponse toResponse(StockReceipt receipt) {
        return new InventoryResponse(receipt.getProductId(), receipt.getAvailable(), receipt.getReserved());
    }
}
