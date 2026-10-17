package com.xuejiai.aaf.module.device.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.device.ErrorCodeConstants;
import com.xuejiai.aaf.module.device.api.DeviceInboundHandler;
import com.xuejiai.aaf.module.device.api.DeviceMessagePublisher;
import com.xuejiai.aaf.module.device.domain.DeviceMessage;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/** HiveMQ MQTT 5 客户端管理器。封装 TLS、会话恢复、自动重连、订阅与发布。 */
@Slf4j
@Component
public class MqttClientManager implements DeviceMessagePublisher {

    private final MqttConnectionConfig config;
    private final DeviceTopicRouter topicRouter;
    private final DeviceDownlinkEnvelopeFactory envelopeFactory;
    private final DeviceRepository deviceRepository;
    private final MqttInboundDispatcher inboundDispatcher;
    private volatile Mqtt5AsyncClient client;

    public MqttClientManager(
            MqttConnectionConfig config,
            DeviceTopicRouter topicRouter,
            DeviceDownlinkEnvelopeFactory envelopeFactory,
            DeviceRepository deviceRepository,
            MqttInboundDispatcher inboundDispatcher) {
        this.config = config;
        this.topicRouter = topicRouter;
        this.envelopeFactory = envelopeFactory;
        this.deviceRepository = deviceRepository;
        this.inboundDispatcher = inboundDispatcher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!config.enabled()) {
            log.info("MQTT 设备通道未启用");
            return;
        }
        var builder =
                MqttClient.builder()
                        .useMqttVersion5()
                        .identifier(config.clientId())
                        .serverHost(config.host())
                        .serverPort(config.port())
                        .sslWithDefaultConfig()
                        .automaticReconnectWithDefaultConfig()
                        .addConnectedListener(context -> subscribeInbound());
        client = builder.buildAsync();
        client.connectWith()
                .cleanStart(false)
                .keepAlive(config.keepAliveSeconds())
                .sessionExpiryInterval(config.sessionExpirySeconds())
                .simpleAuth()
                .username(config.username())
                .password(requirePassword())
                .applySimpleAuth()
                .send()
                .whenComplete(
                        (ignored, failure) -> {
                            if (failure == null) {
                                log.info("MQTT 设备通道已连接: {}:{}", config.host(), config.port());
                            } else {
                                log.error("MQTT 设备通道首次连接失败，将由客户端自动重连", failure);
                            }
                        });
    }

    private void subscribeInbound() {
        var subscriptions =
                java.util.Arrays.stream(DeviceInboundHandler.InboundType.values())
                        .map(this::subscribe)
                        .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(subscriptions)
                .whenComplete(
                        (ignored, failure) -> {
                            if (failure == null) {
                                log.info("MQTT 六类上行主题订阅完成");
                            } else {
                                log.error("MQTT 上行主题订阅失败", failure);
                            }
                        });
    }

    private CompletableFuture<?> subscribe(DeviceInboundHandler.InboundType type) {
        return client.subscribeWith()
                .topicFilter(topicRouter.inboundFilter(config.tenantKey(), type))
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(this::onPublish)
                .send();
    }

    private void onPublish(Mqtt5Publish publish) {
        try {
            inboundDispatcher.dispatch(publish.getTopic().toString(), publish.getPayloadAsBytes());
        } catch (RuntimeException exception) {
            log.warn("拒绝 MQTT 上行消息: {}", exception.getMessage());
        }
    }

    @Override
    public void publish(DeviceMessage message) {
        var active = client;
        if (!config.enabled() || active == null || !active.getState().isConnected()) {
            throw new BusinessException(ErrorCodeConstants.MQTT_CHANNEL_UNAVAILABLE);
        }
        var device =
                deviceRepository
                        .findById(message.getDeviceId())
                        .orElseThrow(
                                () -> new BusinessException(ErrorCodeConstants.DEVICE_NOT_FOUND));
        var payload = envelopeFactory.create(device, message);
        if (payload.length > config.maxPayloadBytes()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_MESSAGE_INVALID);
        }
        active.publishWith()
                .topic(
                        topicRouter.outbound(
                                config.tenantKey(), device.getDeviceKey(), message.getDirection()))
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(false)
                .payload(payload)
                .send()
                .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .join();
    }

    @PreDestroy
    public void disconnect() {
        var active = client;
        if (active != null && active.getState().isConnected()) {
            active.disconnect();
        }
    }

    private byte[] requirePassword() {
        if (config.password() == null || config.password().isBlank()) {
            throw new IllegalStateException("aaf.device.mqtt.password 未配置");
        }
        return config.password().getBytes(StandardCharsets.UTF_8);
    }
}
