package com.xuejiai.aaf.module.device.mqtt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.device.api.DeviceInboundHandler.DeviceInboundMessage;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.mqtt.DeviceTopicRouter.InboundRoute;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/** MQTT 上行信封解析器。拒绝重复字段、未知字段、主题类型不符和非规范载荷。 */
@Component
public class DeviceInboundEnvelopeParser {

    private static final Set<String> ALLOWED_FIELDS =
            Set.of(
                    "specVersion",
                    "messageId",
                    "messageType",
                    "eventId",
                    "commandId",
                    "correlationId",
                    "traceId",
                    "sourceDeviceKey",
                    "bootId",
                    "statusSequence",
                    "deviceOccurredAt",
                    "resultCode",
                    "payloadHash",
                    "payload");

    private final MqttConnectionConfig config;
    private final Rfc8785Canonicalizer canonicalizer;

    public DeviceInboundEnvelopeParser(
            MqttConnectionConfig config, Rfc8785Canonicalizer canonicalizer) {
        this.config = config;
        this.canonicalizer = canonicalizer;
    }

    public DeviceInboundMessage parse(byte[] bytes, InboundRoute route, Device device) {
        if (bytes == null || bytes.length == 0 || bytes.length > config.maxPayloadBytes()) {
            throw new IllegalArgumentException("MQTT 上行消息大小无效");
        }
        var root = JsonUtils.readTreeStrict(new String(bytes, StandardCharsets.UTF_8));
        requireObject(root);
        rejectUnknownFields(root);
        requireEquals(text(root, "specVersion"), "1.0", "不支持的设备协议版本");
        requireEquals(text(root, "sourceDeviceKey"), route.deviceKey(), "设备目标与主题不一致");
        requireEquals(text(root, "sourceDeviceKey"), device.getDeviceKey(), "设备身份不一致");
        requireEquals(text(root, "messageType"), route.type().name(), "消息类型与主题不一致");

        var payload = root.get("payload");
        requireObject(payload);
        var expectedHash = text(root, "payloadHash");
        var actualHash = sha256Hex(canonicalizer.canonicalize(payload));
        if (!constantTimeEquals(expectedHash, actualHash)) {
            throw new IllegalArgumentException("设备载荷哈希不匹配");
        }

        var sequenceNode = root.get("statusSequence");
        if (sequenceNode == null || !sequenceNode.isIntegralNumber() || sequenceNode.asLong() < 0) {
            throw new IllegalArgumentException("statusSequence 必须为非负整数");
        }
        var payloadMap =
                JsonUtils.convertValue(payload, new TypeReference<Map<String, Object>>() {});
        var eventId = boundedText(root, "eventId", 100, false);
        if (route.type() == com.xuejiai.aaf.module.device.api.DeviceInboundHandler.InboundType.ALERT
                && eventId == null) {
            throw new IllegalArgumentException("ALERT 消息必须携带 eventId");
        }
        return new DeviceInboundMessage(
                device.getId(),
                device.getDeviceKey(),
                device.getScenarioKey(),
                route.type(),
                boundedText(root, "messageId", 100, true),
                eventId,
                boundedText(root, "commandId", 100, false),
                boundedText(root, "correlationId", 100, false),
                boundedText(root, "bootId", 100, true),
                sequenceNode.asLong(),
                Instant.parse(text(root, "deviceOccurredAt")),
                boundedText(root, "resultCode", 100, false),
                java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payloadMap)));
    }

    private static void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("设备信封必须为 JSON 对象");
        }
    }

    private static void rejectUnknownFields(JsonNode root) {
        var actual = new LinkedHashSet<String>();
        root.properties().forEach(property -> actual.add(property.getKey()));
        var unknown = new LinkedHashSet<>(actual);
        unknown.removeAll(ALLOWED_FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("设备信封包含未知字段: " + unknown);
        }
    }

    private static String text(JsonNode root, String field) {
        return boundedText(root, field, 200, true);
    }

    private static String boundedText(
            JsonNode root, String field, int maxLength, boolean required) {
        var node = root.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                throw new IllegalArgumentException(field + " 不能为空");
            }
            return null;
        }
        if (!node.isString() || node.asString().isBlank() || node.asString().length() > maxLength) {
            throw new IllegalArgumentException(field + " 格式无效");
        }
        return node.asString();
    }

    private static void requireEquals(String actual, String expected, String message) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static boolean constantTimeEquals(String candidate, String expected) {
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }
}
