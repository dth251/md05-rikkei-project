package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.app.payment.dto.response.VnpayReturnResponse;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.RefundStatus;
import com.app.payment.mapper.VnpayReturnPageMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VnpayReturnPageTests {

    @Test
    void escapesCallbackTextAndRendersSandboxPreview() throws Exception {
        var mapper = new VnpayReturnPageMapper();
        var result = new VnpayReturnResponse("<script>alert(1)</script>", "SUCCESS", "",
                1L, 123L, new BigDecimal("250000"), PaymentStatus.SUCCESS, false, RefundStatus.NONE);
        String page = mapper.render(result, "fixture-nonce");
        assertFalse(page.contains("<script>alert(1)</script>"));
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(page.contains("250.000 ₫"));
        Path directory = Path.of("build", "vnpay-preview");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("success.html"), mapper.render(new VnpayReturnResponse(
                "0123456789abcdef0123456789abcdef", "SUCCESS", "", 1L, 123L,
                new BigDecimal("250000"), PaymentStatus.SUCCESS, false, RefundStatus.NONE), "fixture-nonce"));
        Files.writeString(directory.resolve("pending.html"), mapper.render(new VnpayReturnResponse(
                "0123456789abcdef0123456789abcdef", "SUCCESS", "", 1L, 123L,
                new BigDecimal("250000"), PaymentStatus.PENDING, false, RefundStatus.NONE), "fixture-nonce"));
    }
}
