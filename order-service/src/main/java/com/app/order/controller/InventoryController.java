package com.app.order.controller;

import com.app.order.dto.request.StockRequest;
import com.app.order.dto.response.ApiResponse;
import com.app.order.dto.response.InventoryResponse;
import com.app.order.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> get(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.get(productId), "Inventory retrieved"));
    }

    @PostMapping("/stocks")
    public ResponseEntity<ApiResponse<InventoryResponse>> receive(@Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.receive(request), "Stock received"));
    }
}
