package com.app.order.mapper;

import com.app.order.dto.request.ShippingAddressRequest;
import com.app.order.dto.response.ShippingAddressResponse;
import com.app.order.entity.ShippingAddress;
import org.springframework.stereotype.Component;

@Component
public class ShippingAddressMapper {

    public ShippingAddress toEntity(
            ShippingAddressRequest request
    ) {
        return request == null ? null : new ShippingAddress(request.recipientName().strip(), request.phone().strip(),
                request.street().strip(), request.ward().strip(), request.district().strip(), request.province().strip(),
                request.postalCode() == null || request.postalCode().isBlank() ? null : request.postalCode().strip());
    }

    public ShippingAddressResponse toResponse(
            ShippingAddress address
    ) {
        return address == null ? null : new ShippingAddressResponse(address.getRecipient(), address.getPhone(),
                address.getStreet(), address.getWard(), address.getDistrict(), address.getProvince(), address.getPostalCode());
    }
}
