package com.app.auth.controller;

import com.app.auth.dto.request.AddressRequest;
import com.app.auth.dto.response.AddressResponse;
import com.app.auth.dto.response.ApiResponse;
import com.app.auth.service.AddressService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/addresses")
public class AddressController {

    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> list(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(addresses.list(owner(authentication)), "Addresses retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> get(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(addresses.get(owner(authentication), id), "Address retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> create(
            Authentication authentication,
            @Valid @RequestBody AddressRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(addresses.create(owner(authentication), request), "Address created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(addresses.update(owner(authentication), id, request), "Address updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long id
    ) {
        addresses.delete(owner(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Long owner(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
