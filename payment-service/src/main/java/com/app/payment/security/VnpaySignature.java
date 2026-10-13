package com.app.payment.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnpaySignature {

    private VnpaySignature() {
    }

    public static String canonicalQuery(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty() || parameters.size() > 32) {
            throw new IllegalArgumentException("Invalid provider parameters");
        }
        var ordered = new TreeMap<String, String>();
        parameters.forEach((name, value) -> {
            if (name == null || !name.matches("vnp_[A-Za-z]{1,40}")
                    || value == null || value.length() > 512 || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid provider parameters");
            }
            if (!name.equals("vnp_SecureHash") && !name.equals("vnp_SecureHashType") && !value.isEmpty()) {
                ordered.put(name, value);
            }
        });
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("Provider data required");
        }
        return ordered.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String hmac(String data, String secret) {
        if (data == null || secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Provider signing configuration required");
        }
        try {
            var mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Provider signature unavailable");
        }
    }

    public static boolean verifyQuery(Map<String, String> parameters, String secret) {
        if (parameters == null) {
            return false;
        }
        String signature = parameters.get("vnp_SecureHash");
        if (signature == null || !signature.matches("[0-9a-fA-F]{128}")) {
            return false;
        }
        try {
            byte[] supplied = HexFormat.of().parseHex(signature);
            byte[] expected = HexFormat.of().parseHex(hmac(canonicalQuery(parameters), secret));
            return MessageDigest.isEqual(expected, supplied);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
