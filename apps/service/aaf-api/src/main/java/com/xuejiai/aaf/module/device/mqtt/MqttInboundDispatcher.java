package com.xuejiai.aaf.module.device.mqtt;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.device.api.DeviceInboundHandler;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;
import com.xuejiai.aaf.module.device.service.DevicePresenceService;

import lombok.extern.slf4j.Slf4j;

/** MQTT 上行事务分发器。启动序号投影和业务处理在同一事务内完成。 */
@Slf4j
@Component
public class MqttInboundDispatcher {

    private final MqttConnectionConfig config;
    private final DeviceTopicRouter topicRouter;
    private final DeviceInboundEnvelopeParser inboundParser;
    private final DeviceRepository deviceRepository;
    private final DevicePresenceService presenceService;
    private final List<DeviceInboundHandler> inboundHandlers;

    public MqttInboundDispatcher(
            MqttConnectionConfig config,
            DeviceTopicRouter topicRouter,
            DeviceInboundEnvelopeParser inboundParser,
            DeviceRepository deviceRepository,
            DevicePresenceService presenceService,
            List<DeviceInboundHandler> inboundHandlers) {
        this.config = config;
        this.topicRouter = topicRouter;
        this.inboundParser = inboundParser;
        this.deviceRepository = deviceRepository;
        this.presenceService = presenceService;
        this.inboundHandlers = List.copyOf(inboundHandlers);
    }

    @Transactional
    public void dispatch(String topic, byte[] payload) {
        var route =
                topicRouter
                        .parseInbound(topic)
                        .filter(value -> config.tenantKey().equals(value.tenantKey()))
                        .orElseThrow(() -> new IllegalArgumentException("MQTT 上行主题无效"));
        var device =
                deviceRepository
                        .findByDeviceKey(route.deviceKey())
                        .orElseThrow(() -> new IllegalArgumentException("MQTT 上行设备不存在"));
        var inbound = inboundParser.parse(payload, route, device);
        if (!presenceService.accept(inbound)) {
            log.warn(
                    "忽略旧启动或旧序号的 MQTT 消息: deviceKey={}, bootId={}, sequence={}",
                    inbound.deviceKey(),
                    inbound.bootId(),
                    inbound.statusSequence());
            return;
        }
        inboundHandlers.stream()
                .filter(
                        handler ->
                                handler.supports(inbound.type())
                                        && handler.supportsScenario(inbound.scenarioKey()))
                .forEach(handler -> handler.handle(inbound));
    }
}
