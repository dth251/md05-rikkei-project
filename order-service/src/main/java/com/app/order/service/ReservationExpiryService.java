package com.app.order.service;

import java.time.Instant;
import java.util.List;

public interface ReservationExpiryService {

    List<Long> candidates(Instant cutoff);

    boolean expire(Long orderId, Instant cutoff);
}
