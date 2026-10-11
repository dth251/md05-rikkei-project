package com.app.order.service.impl;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.request.StockRequest;
import com.app.order.dto.response.InventoryResponse;
import com.app.order.entity.StockReceipt;
import com.app.order.mapper.StockReceiptMapper;
import com.app.order.repository.StockReceiptRepository;
import com.app.order.repository.StockRepository;
import com.app.order.service.StockReceiptTransactions;
import com.app.order.repository.StockReceiptClaimRepository;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReceiptTransactionsImpl implements StockReceiptTransactions {

    private final StockReceiptRepository receipts;
    private final StockRepository stocks;
    private final StockReceiptMapper mapper;
    private final StockReceiptClaimRepository claims;

    public StockReceiptTransactionsImpl(
        StockReceiptRepository receipts,
        StockRepository stocks,
        StockReceiptMapper mapper,
        StockReceiptClaimRepository claims
    ) {
        this.receipts = receipts;
        this.stocks = stocks;
        this.mapper = mapper;
        this.claims = claims;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryResponse> replay(StockRequest request) {
        return receipts.findById(request.idempotencyKey()).map(receipt -> {
            if (!receipt.matches(request.productId(), request.quantity())) {
                throw new ApiException(HttpStatus.CONFLICT, "Receipt key already used with another request");
            }
            return mapper.toResponse(receipt);
        });
    }

    @Override
    @Transactional
    public InventoryResponse receive(StockRequest request) {
        StockReceipt receipt = new StockReceipt(request.idempotencyKey(), request.productId(), request.quantity());
        // Claim the unique key before stock changes. A concurrent duplicate waits for commit.
        claims.claim(receipt);
        if (stocks.receive(request.productId(), request.quantity()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Stock exceeds supported quantity");
        }
        InventoryResponse response = stocks.find(request.productId()).orElseThrow();
        receipt.snapshot(response.available(), response.reserved());
        return response;
    }
}
