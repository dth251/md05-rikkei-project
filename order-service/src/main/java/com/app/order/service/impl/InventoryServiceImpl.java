package com.app.order.service.impl;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.request.StockRequest;
import com.app.order.dto.response.InventoryResponse;
import com.app.order.repository.StockRepository;
import com.app.order.service.InventoryService;
import com.app.order.service.ProductCatalog;
import com.app.order.service.StockReceiptTransactions;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final StockRepository stockRepository;
    private final ProductCatalog productCatalog;
    private final StockReceiptTransactions receipts;

    public InventoryServiceImpl(
        StockRepository stockRepository,
        ProductCatalog productCatalog,
        StockReceiptTransactions receipts
    ) {
        this.stockRepository = stockRepository;
        this.productCatalog = productCatalog;
        this.receipts = receipts;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse get(Long productId) {
        return stockRepository.find(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Inventory not found"));
    }

    @Override
    public InventoryResponse receive(StockRequest request) {
        var previous = receipts.replay(request);
        if (previous.isPresent()) {
            return previous.get();
        }
        productCatalog.get(request.productId());
        try {
            return receipts.receive(request);
        } catch (DataIntegrityViolationException exception) {
            // The failed transaction has rolled back before replay reads the winning receipt.
            return receipts.replay(request).orElseThrow(() -> exception);
        }
    }
}
