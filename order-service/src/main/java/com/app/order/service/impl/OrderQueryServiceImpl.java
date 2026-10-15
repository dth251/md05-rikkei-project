package com.app.order.service.impl;

import com.app.order.dto.request.OrderSearchRequest;
import com.app.order.dto.response.OrderPageResponse;
import com.app.order.entity.PurchaseOrder;
import com.app.order.mapper.OrderMapper;
import com.app.order.repository.OrderRepository;
import com.app.order.service.OrderQueryService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderQueryServiceImpl implements OrderQueryService {

    private final OrderRepository orders;
    private final OrderMapper mapper;

    public OrderQueryServiceImpl(OrderRepository orders, OrderMapper mapper) {
        this.orders = orders;
        this.mapper = mapper;
    }

    @Override
    public OrderPageResponse list(Long userId, boolean administrator, OrderSearchRequest request) {
        Specification<PurchaseOrder> filter = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (!administrator) {
                predicates.add(builder.equal(root.get("userId"), userId));
            }
            if (request.status() != null) {
                predicates.add(builder.equal(root.get("status"), request.status()));
            }
            if (request.channel() != null) {
                predicates.add(builder.equal(root.get("channel"), request.channel()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        var page = orders.findAll(filter, PageRequest.of(request.pageValue(), request.sizeValue(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new OrderPageResponse(page.getContent().stream().map(mapper::toSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
