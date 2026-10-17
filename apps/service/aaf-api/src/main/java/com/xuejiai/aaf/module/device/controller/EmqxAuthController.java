package com.xuejiai.aaf.module.device.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.module.device.mqtt.DeviceTopicRouter;
import com.xuejiai.aaf.module.device.mqtt.MqttConnectionConfig;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;
import com.xuejiai.aaf.module.device.service.DeviceCredentialService;

/** EMQX 认证与主题授权内部回调。共享密钥错误时不执行任何凭据查询。 */
@RestController
@RequestMapping("/api/internal/emqx")
public class EmqxAuthController {

    private static final String SECRET_HEADER = "X-Emqx-Secret";

    private final MqttConnectionConfig config;
    private final DeviceCredentialService credentialService;
    private final DeviceRepository deviceRepository;
    private final DeviceTopicRouter topicRouter;

    public EmqxAuthController(
            MqttConnectionConfig config,
            DeviceCredentialService credentialService,
            DeviceRepository deviceRepository,
            DeviceTopicRouter topicRouter) {
        this.config = config;
        this.credentialService = credentialService;
        this.deviceRepository = deviceRepository;
        this.topicRouter = topicRouter;
    }

    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> authenticate(
            @RequestHeader(name = SECRET_HEADER, required = false) String sharedSecret,
            @RequestBody(required = false) AuthRequest request) {
        if (!validSharedSecret(sharedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var allowed = serviceAuthenticated(request);
        if (!allowed && request != null) {
            allowed =
                    constantEquals(request.clientid(), request.username())
                            & credentialService
                                    .authenticate(request.username(), request.password())
                                    .isPresent();
        }
        return ResponseEntity.ok(
                Map.of("result", allowed ? "allow" : "deny", "is_superuser", false));
    }

    @PostMapping("/acl")
    public ResponseEntity<Map<String, Object>> authorize(
            @RequestHeader(name = SECRET_HEADER, required = false) String sharedSecret,
            @RequestBody(required = false) AclRequest request) {
        if (!validSharedSecret(sharedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var allowed = request != null && serviceAuthorized(request);
        if (!allowed && request != null && constantEquals(request.clientid(), request.username())) {
            var device = deviceRepository.findByDeviceKey(request.username()).orElse(null);
            allowed =
                    device != null
                            && DeviceStatusEnum.BOUND.getCode().equals(device.getStatus())
                            && device.getCredentialRevokedAt() == null
                            && topicRouter.deviceAuthorized(
                                    config.tenantKey(),
                                    device.getDeviceKey(),
                                    request.action(),
                                    request.topic(),
                                    qosValue(request.qos()),
                                    retainedValue(request.retain()));
        }
        return ResponseEntity.ok(Map.of("result", allowed ? "allow" : "deny"));
    }

    private boolean serviceAuthenticated(AuthRequest request) {
        if (request == null || config.password() == null || config.password().isBlank()) {
            return false;
        }
        return constantEquals(request.clientid(), config.clientId())
                & constantEquals(request.username(), config.username())
                & constantEquals(request.password(), config.password());
    }

    private boolean serviceAuthorized(AclRequest request) {
        return constantEquals(request.clientid(), config.clientId())
                & constantEquals(request.username(), config.username())
                & topicRouter.serviceAuthorized(
                        config.tenantKey(), request.action(), request.topic());
    }

    private boolean validSharedSecret(String candidate) {
        var configured = config.emqx().sharedSecret();
        return configured != null && !configured.isBlank() && constantEquals(candidate, configured);
    }

    private static boolean constantEquals(String candidate, String expected) {
        return MessageDigest.isEqual(
                (candidate == null ? "" : candidate).getBytes(StandardCharsets.UTF_8),
                (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8));
    }

    private static int qosValue(tools.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return -1;
        }
        if (node.isIntegralNumber()) {
            return node.asInt();
        }
        if (node.isString()) {
            try {
                return Integer.parseInt(node.asString());
            } catch (NumberFormatException exception) {
                return -1;
            }
        }
        return -1;
    }

    private static boolean retainedValue(tools.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        return node.isBoolean() ? node.asBoolean() : Boolean.parseBoolean(node.asString());
    }

    public record AuthRequest(String clientid, String username, String password, String peerhost) {}

    public record AclRequest(
            String clientid,
            String username,
            String action,
            String topic,
            tools.jackson.databind.JsonNode qos,
            tools.jackson.databind.JsonNode retain,
            String peerhost) {}
}
