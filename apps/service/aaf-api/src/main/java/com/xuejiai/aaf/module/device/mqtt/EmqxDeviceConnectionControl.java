package com.xuejiai.aaf.module.device.mqtt;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.xuejiai.aaf.module.device.api.DeviceConnectionControl;

/** 通过 EMQX 管理 API 断开设备 MQTT 客户端。 */
@Component
public class EmqxDeviceConnectionControl implements DeviceConnectionControl {

    private final MqttConnectionConfig config;
    private final RestClient.Builder restClientBuilder;

    public EmqxDeviceConnectionControl(
            MqttConnectionConfig config, RestClient.Builder restClientBuilder) {
        this.config = config;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public void disconnect(String deviceKey) {
        if (deviceKey == null || deviceKey.isBlank()) {
            throw new IllegalArgumentException("deviceKey 不能为空");
        }
        var emqx = config.emqx();
        requireConfigured(emqx.apiUsername(), "EMQX API 用户名");
        requireConfigured(emqx.apiPassword(), "EMQX API 密码");
        try {
            restClientBuilder
                    .clone()
                    .baseUrl(emqx.apiBaseUrl())
                    .defaultHeaders(
                            headers -> headers.setBasicAuth(emqx.apiUsername(), emqx.apiPassword()))
                    .build()
                    .delete()
                    .uri(builder -> builder.path("/api/v5/clients/").pathSegment(deviceKey).build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
                throw exception;
            }
        }
    }

    private static void requireConfigured(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + "未配置");
        }
    }
}
