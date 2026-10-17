package com.xuejiai.aaf.module.device.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 匿名设备配对请求与响应契约。 */
public final class DevicePairingVO {

    private DevicePairingVO() {}

    @Schema(description = "开始设备配对")
    public record StartRequest(
            @NotBlank @Size(max = 100) String serialNumber,
            @Size(max = 100) String model,
            @Size(max = 64) String firmwareVersion) {}

    @Schema(description = "轮询设备配对结果")
    public record PollRequest(@NotBlank @Size(max = 128) String deviceCode) {}

    @Schema(description = "设备配对启动结果")
    public record StartResponse(
            String pairCode, String deviceCode, LocalDateTime expiresAt, String qrContent) {}

    @Schema(description = "设备配对轮询结果")
    public record PollResponse(
            String status,
            String deviceKey,
            String credential,
            MqttAccess mqtt,
            String apiBaseUrl) {

        public static PollResponse pending() {
            return new PollResponse("PENDING", null, null, null, null);
        }

        public static PollResponse delivered() {
            return new PollResponse("DELIVERED", null, null, null, null);
        }
    }

    @Schema(description = "设备 MQTT 接入参数")
    public record MqttAccess(
            String host,
            int port,
            boolean tls,
            String clientId,
            String username,
            String tenantKey,
            int keepAliveSeconds,
            long sessionExpirySeconds) {}
}
