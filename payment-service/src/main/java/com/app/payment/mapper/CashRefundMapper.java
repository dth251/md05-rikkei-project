package com.app.payment.mapper;

import com.app.payment.dto.response.CashRefundResponse;
import com.app.payment.entity.CashRefund;
import org.springframework.stereotype.Component;

@Component
public class CashRefundMapper {

    public CashRefundResponse toResponse(CashRefund record) {
        return new CashRefundResponse(record.getPaymentId(), record.getAmount(), record.getReceiptNumber(),
                record.getRefundedBy(), record.getRefundedAt());
    }
}
