package com.app.payment.security;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class VnpayLedgerSignature {

    private static final List<String> REFUND_REQUEST = List.of("vnp_RequestId", "vnp_Version", "vnp_Command",
            "vnp_TmnCode", "vnp_TransactionType", "vnp_TxnRef", "vnp_Amount", "vnp_TransactionNo",
            "vnp_TransactionDate", "vnp_CreateBy", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo");
    private static final List<String> QUERY_REQUEST = List.of("vnp_RequestId", "vnp_Version", "vnp_Command",
            "vnp_TmnCode", "vnp_TxnRef", "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo");
    private static final List<String> REFUND_RESPONSE = List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode",
            "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
            "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo");
    private static final List<String> QUERY_RESPONSE = java.util.stream.Stream.concat(REFUND_RESPONSE.stream(),
            java.util.stream.Stream.of("vnp_PromotionCode", "vnp_PromotionAmount")).toList();

    private VnpayLedgerSignature() {
    }

    public static String requestData(Map<String, String> parameters) {
        return join(parameters, switch (parameters.getOrDefault("vnp_Command", "")) {
            case "refund" -> REFUND_REQUEST;
            case "querydr" -> QUERY_REQUEST;
            default -> throw new IllegalArgumentException("Invalid ledger command");
        });
    }

    public static String responseData(Map<String, String> parameters, String command) {
        return join(parameters, switch (command) {
            case "refund" -> REFUND_RESPONSE;
            case "querydr" -> QUERY_RESPONSE;
            default -> throw new IllegalArgumentException("Invalid ledger command");
        });
    }

    public static boolean verifyResponse(Map<String, String> parameters, String command, String secret) {
        String hash = parameters.get("vnp_SecureHash");
        if (hash == null || !hash.matches("[0-9a-fA-F]{128}")) {
            return false;
        }
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(hash), HexFormat.of().parseHex(
                    VnpaySignature.hmac(responseData(parameters, command), secret)));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String join(Map<String, String> parameters, List<String> names) {
        return names.stream().map(name -> {
            String value = parameters.getOrDefault(name, "");
            if (value == null || value.length() > 512 || value.indexOf('|') >= 0
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid ledger parameters");
            }
            return value;
        }).collect(Collectors.joining("|"));
    }
}
