package com.app.payment.dto.response;

import java.time.Instant;

public record CodReceiptResponse(
        String receiptNumber,
        Long collectedBy,
        Instant collectedAt
) {
}
