package com.xuejiai.aaf.module.device.mqtt;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.device.DeviceMessageDirectionEnum;
import com.xuejiai.aaf.module.device.api.DeviceInboundHandler.InboundType;

/** MQTT 主题唯一构造入口。业务代码、工具和模型不得自行拼接主题。 */
@Component
public class DeviceTopicRouter {

    private static final Pattern OPAQUE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final String PREFIX = "/aaf/v1/";
    private static final Set<String> DOWN_SUFFIXES = Set.of("command", "cancel", "config");
    private static final Set<String> UP_SUFFIXES =
            Set.of("presence", "status", "alert", "ack", "result", "capabilities");

    public String outbound(String tenantKey, String deviceKey, String direction) {
        var suffix =
                switch (DeviceMessageDirectionEnum.valueOf(direction)) {
                    case COMMAND -> "command";
                    case CANCEL -> "cancel";
                    case CONFIG -> "config";
                };
        return topic(tenantKey, deviceKey, "down", suffix);
    }

    public String inboundFilter(String tenantKey, InboundType type) {
        var suffix = type.name().toLowerCase(Locale.ROOT);
        return topic(tenantKey, "+", "up", suffix);
    }

    public Optional<InboundRoute> parseInbound(String topic) {
        if (topic == null || !topic.startsWith(PREFIX)) {
            return Optional.empty();
        }
        var parts = topic.substring(PREFIX.length()).split("/", -1);
        if (parts.length != 4
                || !validKey(parts[0])
                || !validKey(parts[1])
                || !"up".equals(parts[2])
                || !UP_SUFFIXES.contains(parts[3])) {
            return Optional.empty();
        }
        return Optional.of(
                new InboundRoute(
                        parts[0],
                        parts[1],
                        InboundType.valueOf(parts[3].toUpperCase(Locale.ROOT))));
    }

    public boolean deviceAuthorized(
            String tenantKey,
            String deviceKey,
            String action,
            String requestedTopic,
            int qos,
            boolean retained) {
        if (qos < 0 || qos > 1 || !validKey(tenantKey) || !validKey(deviceKey)) {
            return false;
        }
        var base = PREFIX + tenantKey + "/" + deviceKey + "/";
        if ("subscribe".equalsIgnoreCase(action)) {
            if ((base + "down/#").equals(requestedTopic)) {
                return true;
            }
            return hasAllowedSuffix(requestedTopic, base + "down/", DOWN_SUFFIXES);
        }
        if (!"publish".equalsIgnoreCase(action)
                || !hasAllowedSuffix(requestedTopic, base + "up/", UP_SUFFIXES)) {
            return false;
        }
        var retainedAllowed =
                requestedTopic.endsWith("/presence") || requestedTopic.endsWith("/capabilities");
        return !retained || retainedAllowed;
    }

    public boolean serviceAuthorized(String tenantKey, String action, String requestedTopic) {
        if (requestedTopic == null || !validKey(tenantKey) || !requestedTopic.startsWith(PREFIX)) {
            return false;
        }
        var parts = requestedTopic.substring(PREFIX.length()).split("/", -1);
        if (parts.length != 4 || !tenantKey.equals(parts[0])) {
            return false;
        }
        if ("subscribe".equalsIgnoreCase(action)) {
            return "+".equals(parts[1]) && "up".equals(parts[2]) && UP_SUFFIXES.contains(parts[3]);
        }
        return "publish".equalsIgnoreCase(action)
                && validKey(parts[1])
                && "down".equals(parts[2])
                && DOWN_SUFFIXES.contains(parts[3]);
    }

    private String topic(String tenantKey, String deviceKey, String direction, String suffix) {
        requireKey(tenantKey, "tenantKey");
        if (!"+".equals(deviceKey)) {
            requireKey(deviceKey, "deviceKey");
        }
        return PREFIX + tenantKey + "/" + deviceKey + "/" + direction + "/" + suffix;
    }

    private static boolean hasAllowedSuffix(String topic, String base, Set<String> suffixes) {
        if (topic == null || !topic.startsWith(base)) {
            return false;
        }
        return suffixes.contains(topic.substring(base.length()));
    }

    private static boolean validKey(String value) {
        return value != null && OPAQUE_KEY.matcher(value).matches();
    }

    private static void requireKey(String value, String field) {
        if (!validKey(value)) {
            throw new IllegalArgumentException(field + " 不是有效的不透明键");
        }
    }

    public record InboundRoute(String tenantKey, String deviceKey, InboundType type) {}
}
