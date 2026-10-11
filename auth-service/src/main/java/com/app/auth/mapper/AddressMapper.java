package com.app.auth.mapper;

import com.app.auth.dto.response.AddressResponse;
import com.app.auth.entity.Address;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public AddressResponse toResponse(Address address) {
        return new AddressResponse(address.getId(), address.getRecipientName(), address.getPhone(),
                address.getStreet(), address.getWard(), address.getDistrict(), address.getProvince(),
                address.getPostalCode(), address.isDefaultAddress());
    }
}
