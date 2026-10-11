package com.app.auth.service;

import com.app.auth.dto.request.AddressRequest;
import com.app.auth.dto.response.AddressResponse;
import java.util.List;

public interface AddressService {

    List<AddressResponse> list(Long userId);

    AddressResponse get(Long userId, Long id);

    AddressResponse create(Long userId, AddressRequest request);

    AddressResponse update(Long userId, Long id, AddressRequest request);

    void delete(Long userId, Long id);
}
