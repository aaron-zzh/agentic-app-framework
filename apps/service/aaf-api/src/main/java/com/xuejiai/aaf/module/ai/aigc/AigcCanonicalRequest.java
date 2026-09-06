package com.xuejiai.aaf.module.ai.aigc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.xuejiai.aaf.common.util.JsonUtils;

/** 公开幂等命令的唯一 canonical 表达，业务字段与 CAS 字段分区后共同参与摘要。 */
public record AigcCanonicalRequest(
        String operation, Map<String, Object> business, Map<String, Object> cas) {

    public AigcCanonicalRequest {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation 不能为空");
        }
        business = canonicalMap(business);
        cas = canonicalMap(cas);
    }

    public static AigcCanonicalRequest of(
            String operation, Map<String, Object> business, Map<String, Object> cas) {
        return new AigcCanonicalRequest(operation, business, cas);
    }

    public String sha256() {
        try {
            var bytes = JsonUtils.toJsonString(this).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK 不支持 SHA-256", error);
        }
    }

    private static Map<String, Object> canonicalMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        var result = new TreeMap<String, Object>();
        source.forEach((key, value) -> result.put(key, canonicalValue(value)));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, item) -> result.put(String.valueOf(key), canonicalValue(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AigcCanonicalRequest::canonicalValue).toList();
        }
        return value;
    }
}
