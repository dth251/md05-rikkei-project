package com.app.order.service.impl;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.request.StockRequest;
import com.app.order.dto.response.InventoryResponse;
import com.app.order.repository.StockRepository;
import com.app.order.service.InventoryService;
import com.app.order.service.ProductCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final StockRepository stockRepository;
    private final ProductCatalog productCatalog;

    public InventoryServiceImpl(
        StockRepository stockRepository,
        ProductCatalog productCatalog
    ) {
        this.stockRepository = stockRepository;
        this.productCatalog = productCatalog;
    }

    @Override
    public InventoryResponse get(Long productId) {
        return stockRepository.find(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Inventory not found"));
    }

    @Override
    @Transactional
    public InventoryResponse receive(StockRequest request) {
        productCatalog.get(request.productId());
        if (stockRepository.receive(request.productId(), request.quantity()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Stock exceeds supported quantity");
        }
        return get(request.productId());
    }
}
