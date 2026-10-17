package com.xuejiai.aaf.module.device.mqtt;

import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;

import tools.jackson.databind.JsonNode;

/** RFC 8785 JSON 规范化器。数字按 ECMAScript 的有限 IEEE-754 表示规范化。 */
@Component
public class Rfc8785Canonicalizer {

    public byte[] canonicalize(JsonNode node) {
        return canonicalString(node).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public String canonicalString(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            var builder = new StringBuilder("{");
            var properties =
                    node.properties().stream()
                            .sorted(
                                    Comparator.comparing(
                                            java.util.Map.Entry<String, JsonNode>::getKey))
                            .toList();
            for (var index = 0; index < properties.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                var property = properties.get(index);
                builder.append(JsonUtils.toJsonString(property.getKey()))
                        .append(':')
                        .append(canonicalString(property.getValue()));
            }
            return builder.append('}').toString();
        }
        if (node.isArray()) {
            var builder = new StringBuilder("[");
            for (var index = 0; index < node.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(canonicalString(node.get(index)));
            }
            return builder.append(']').toString();
        }
        if (node.isString()) {
            return JsonUtils.toJsonString(node.asString());
        }
        if (node.isBoolean()) {
            return Boolean.toString(node.asBoolean());
        }
        if (node.isNumber()) {
            return canonicalNumber(node.doubleValue());
        }
        throw new IllegalArgumentException("设备消息包含不受支持的 JSON 值");
    }

    private static String canonicalNumber(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("设备消息数字必须有限");
        }
        if (value == 0D) {
            return "0";
        }
        var decimal = java.math.BigDecimal.valueOf(value).stripTrailingZeros();
        var absolute = Math.abs(value);
        if (absolute >= 1.0e-6 && absolute < 1.0e21) {
            return decimal.toPlainString();
        }
        var sign = decimal.signum() < 0 ? "-" : "";
        var digits = decimal.unscaledValue().abs().toString();
        var exponent = digits.length() - decimal.scale() - 1;
        var mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return sign + mantissa + "e" + (exponent >= 0 ? "+" : "") + exponent;
    }
}
