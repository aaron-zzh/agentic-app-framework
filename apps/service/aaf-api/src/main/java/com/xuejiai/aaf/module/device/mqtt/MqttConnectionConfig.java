package com.xuejiai.aaf.module.device.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MQTT 设备通道配置。密钥字段必须由环境变量、配置中心或加密配置注入。 */
@ConfigurationProperties(prefix = "aaf.device.mqtt")
public record MqttConnectionConfig(
        boolean enabled,
        String host,
        int port,
        String clientId,
        String username,
        String password,
        String tenantKey,
        int keepAliveSeconds,
        long sessionExpirySeconds,
        int ackTimeoutSeconds,
        int maxPayloadBytes,
        Emqx emqx,
        Signing signing) {

    public MqttConnectionConfig {
        host = defaultText(host, "localhost");
        port = port <= 0 ? 8883 : port;
        clientId = defaultText(clientId, "aaf-device-service");
        username = defaultText(username, "aaf-device-service");
        tenantKey = defaultText(tenantKey, "default");
        keepAliveSeconds = keepAliveSeconds <= 0 ? 60 : keepAliveSeconds;
        sessionExpirySeconds = sessionExpirySeconds <= 0 ? 86_400 : sessionExpirySeconds;
        ackTimeoutSeconds = ackTimeoutSeconds <= 0 ? 30 : ackTimeoutSeconds;
        maxPayloadBytes = maxPayloadBytes <= 0 ? 65_536 : maxPayloadBytes;
        emqx = emqx == null ? new Emqx(null, null, null, null) : emqx;
        signing = signing == null ? new Signing(null, null, null) : signing;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Emqx(
            String sharedSecret, String apiBaseUrl, String apiUsername, String apiPassword) {
        public Emqx {
            apiBaseUrl = defaultText(apiBaseUrl, "http://localhost:18083");
        }
    }

    public record Signing(String keyId, String privateKey, String publicKey) {
        public Signing {
            keyId = defaultText(keyId, "aaf-device-command");
        }
    }
}
