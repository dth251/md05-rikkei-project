package com.app.product.mapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class VariantMapper {

    public Map<String, String> normalize(
            Map<String, String> attributes
    ) {
        var result = new TreeMap<String, String>();
        attributes.forEach((key, value) -> result.put(key,
                Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(java.util.Locale.ROOT)));
        return result;
    }

    public String key(
            Map<String, String> normalized
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            new TreeMap<>(normalized).forEach((name, value) -> {
                byte[] keyBytes = name.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(keyBytes.length).array());
                digest.update(keyBytes);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(valueBytes.length).array());
                digest.update(valueBytes);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm unavailable");
        }
    }
}
