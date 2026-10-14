package com.app.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.app.payment.config.VnpaySettings;
import com.app.payment.config.exception.ApiException;
import com.app.payment.service.VnpayTransactionService;
import com.app.payment.service.impl.VnpayServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;

class VnpayFacadeTests {

    @Test
    void disabledProviderDoesNotTouchTransactions() {
        var transactions = mock(VnpayTransactionService.class);
        var service = new VnpayServiceImpl(new VnpaySettings(false, "", "", ""), transactions, mock(com.app.payment.service.VnpayPaymentQueryService.class));
        assertEquals("99", service.ipn(new LinkedMultiValueMap<>()).responseCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ApiException.class, () -> service.checkout(1L, 10L, "127.0.0.1")).getStatus());
        assertThrows(ApiException.class, () -> service.verifyReturn(new LinkedMultiValueMap<>()));
        verifyNoInteractions(transactions);
    }

    @Test
    void invalidSignatureIsRejectedBeforeDatabaseAccess() {
        var transactions = mock(VnpayTransactionService.class);
        var settings = new VnpaySettings(true, "FIXTURE1", "public-fixture-signing-key-only",
                "https://example.com/api/payments/vnpay/return");
        var service = new VnpayServiceImpl(settings, transactions, mock(com.app.payment.service.VnpayPaymentQueryService.class));
        var input = new LinkedMultiValueMap<String, String>();
        input.set("vnp_SecureHash", "a".repeat(128));
        input.set("vnp_Amount", "1250");
        assertEquals("97", service.ipn(input).responseCode());
        verifyNoInteractions(transactions);
        assertFalse(settings.toString().contains("public-fixture-signing-key-only"));
    }
}
