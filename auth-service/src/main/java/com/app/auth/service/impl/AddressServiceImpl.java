package com.app.auth.service.impl;

import com.app.auth.config.exception.ApiException;
import com.app.auth.dto.request.AddressRequest;
import com.app.auth.dto.response.AddressResponse;
import com.app.auth.entity.Address;
import com.app.auth.entity.User;
import com.app.auth.mapper.AddressMapper;
import com.app.auth.repository.AddressRepository;
import com.app.auth.repository.UserRepository;
import com.app.auth.service.AddressService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addresses;
    private final UserRepository users;
    private final AddressMapper mapper;

    public AddressServiceImpl(
            AddressRepository addresses,
            UserRepository users,
            AddressMapper mapper
    ) {
        this.addresses = addresses;
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addresses.findAllByUser_IdOrderByIdAsc(userId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse get(
            Long userId,
            Long id
    ) {
        return mapper.toResponse(owned(userId, id));
    }

    @Override
    @Transactional
    public AddressResponse create(
            Long userId,
            AddressRequest request
    ) {
        User user = lockOwner(userId);
        List<Address> existing = addresses.findAllByUser_IdOrderByIdAsc(userId);
        if (existing.size() >= 10) {
            throw new ApiException(HttpStatus.CONFLICT, "Maximum ten addresses per user");
        }
        boolean makeDefault = existing.isEmpty() || request.defaultAddress();
        if (makeDefault) {
            clearDefault(existing);
        }
        return mapper.toResponse(addresses.saveAndFlush(new Address(user, request, makeDefault)));
    }

    @Override
    @Transactional
    public AddressResponse update(
            Long userId,
            Long id,
            AddressRequest request
    ) {
        lockOwner(userId);
        Address address = owned(userId, id);
        List<Address> existing = addresses.findAllByUser_IdOrderByIdAsc(userId);
        boolean wasDefault = address.isDefaultAddress();
        boolean makeDefault = request.defaultAddress() || existing.size() == 1;
        if (makeDefault) {
            clearDefault(existing);
        }
        address.replace(request, makeDefault);
        addresses.flush();
        if (wasDefault && !makeDefault) {
            existing.stream().filter(other -> !other.getId().equals(id)).findFirst().orElseThrow()
                    .makeDefault(true);
            addresses.flush();
        }
        return mapper.toResponse(address);
    }

    @Override
    @Transactional
    public void delete(
            Long userId,
            Long id
    ) {
        lockOwner(userId);
        Address address = owned(userId, id);
        boolean wasDefault = address.isDefaultAddress();
        addresses.delete(address);
        addresses.flush();
        if (wasDefault) {
            addresses.findAllByUser_IdOrderByIdAsc(userId).stream().findFirst()
                    .ifPresent(next -> next.makeDefault(true));
            addresses.flush();
        }
    }

    private void clearDefault(List<Address> existing) {
        existing.forEach(address -> address.makeDefault(false));
        addresses.flush();
    }

    private User lockOwner(Long userId) {
        return users.findLockedById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Address owned(
            Long userId,
            Long id
    ) {
        return addresses.findByIdAndUser_Id(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Address not found"));
    }
}
