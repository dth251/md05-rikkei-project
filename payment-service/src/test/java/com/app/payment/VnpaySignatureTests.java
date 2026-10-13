package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.app.payment.security.VnpaySignature;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VnpaySignatureTests {

    @Test
    void hmacMatchesIndependentRfc4231Vector() {
        // RFC 4231 section 4.3, public test key/data, not merchant credentials.
        assertEquals("164b7a7bfcf819e2e395fbe73b56e0a3"
                        + "87bd64222e831fd610270cd7ea250554"
                        + "9758bf75c05a994a6d034f65f8f0e6fd"
                        + "caeab1a34d4a6b4b636e070a38bce737",
                VnpaySignature.hmac("what do ya want for nothing?", "Jefe"));
    }

    @Test
    void canonicalQuerySortsRawNamesAndEncodesOnce() {
        assertEquals("vnp_Amount=1250&vnp_OrderInfo=Order+%26+1%2B2%2F3&vnp_TxnRef=abc",
                VnpaySignature.canonicalQuery(Map.of("vnp_TxnRef", "abc", "vnp_Amount", "1250",
                        "vnp_OrderInfo", "Order & 1+2/3", "vnp_BankCode", "")));
        assertEquals("vnp_OrderInfo=%C4%91", VnpaySignature.canonicalQuery(Map.of("vnp_OrderInfo", "đ")));
    }

    @Test
    void hashAndLegacyHashTypeAreExcludedFromSignedData() {
        assertEquals("vnp_Amount=1250", VnpaySignature.canonicalQuery(Map.of("vnp_Amount", "1250",
                "vnp_SecureHash", "a".repeat(128), "vnp_SecureHashType", "HMACSHA512")));
    }

    @Test
    void modifiedAmountAndWrongSecretCannotVerify() {
        var data = new HashMap<>(Map.of("vnp_Amount", "1250", "vnp_TxnRef", "fixture"));
        data.put("vnp_SecureHash", VnpaySignature.hmac(VnpaySignature.canonicalQuery(data), "fixture-key"));
        assertTrue(VnpaySignature.verifyQuery(data, "fixture-key"));
        assertFalse(VnpaySignature.verifyQuery(data, "other-fixture-key"));
        data.put("vnp_Amount", "1251");
        assertFalse(VnpaySignature.verifyQuery(data, "fixture-key"));
    }

    @Test
    void uppercaseHexIsAcceptedButMalformedAndTruncatedHashIsRejected() {
        var data = new HashMap<>(Map.of("vnp_Amount", "1250"));
        String hash = VnpaySignature.hmac(VnpaySignature.canonicalQuery(data), "fixture-key");
        data.put("vnp_SecureHash", hash.toUpperCase(Locale.ROOT));
        assertTrue(VnpaySignature.verifyQuery(data, "fixture-key"));
        for (String invalid : new String[]{"", hash.substring(0, 127), "g".repeat(128), hash + "0"}) {
            data.put("vnp_SecureHash", invalid);
            assertFalse(VnpaySignature.verifyQuery(data, "fixture-key"));
        }
        assertFalse(VnpaySignature.verifyQuery(null, "fixture-key"));
    }

    @Test
    void malformedNamesOversizeAndControlCharactersCannotBeSigned() {
        for (var invalid : List.of(Map.of("other", "1"), Map.of("vnp_OrderInfo", "a".repeat(513)),
                Map.of("vnp_OrderInfo", "first\nsecond"), Map.of("vnp_Amount", ""))) {
            assertThrows(IllegalArgumentException.class, () -> VnpaySignature.canonicalQuery(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> VnpaySignature.hmac("data", ""));
    }
}
