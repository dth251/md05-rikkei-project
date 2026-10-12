package com.app.order.mapper;

import com.app.order.dto.response.ProductSnapshot;
import com.app.order.grpc.generated.ProductsReply;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductRpcMapper {

    public List<ProductSnapshot> toSnapshots(
            ProductsReply reply,
            List<Long> requestedIds
    ) {
        var snapshots = new HashMap<Long, ProductSnapshot>();
        for (var product : reply.getProductsList()) {
            String amount = product.getUnitPrice();
            if (!requestedIds.contains(product.getId()) || !product.getActive()
                    || !product.getSku().matches("[A-Z0-9][A-Z0-9._-]{0,63}")
                    || product.getName().isBlank() || product.getName().length() > 200
                    || !amount.matches("[0-9]{1,12}(\\.[0-9]{1,2})?")) {
                throw new IllegalArgumentException("Invalid catalog reply");
            }
            BigDecimal price = new BigDecimal(amount);
            if (price.signum() <= 0 || snapshots.put(product.getId(), new ProductSnapshot(
                    product.getId(), product.getSku(), product.getName(), price)) != null) {
                throw new IllegalArgumentException("Invalid catalog reply");
            }
        }
        if (snapshots.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Incomplete catalog reply");
        }
        return requestedIds.stream().map(snapshots::get).toList();
    }
}
