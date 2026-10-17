package com.xuejiai.aaf.module.device.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.device.DeviceMessageDirectionEnum;
import com.xuejiai.aaf.common.enums.device.DeviceMessageStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.device.ErrorCodeConstants;
import com.xuejiai.aaf.module.device.api.DeviceMessageApi;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.domain.DeviceMessage;
import com.xuejiai.aaf.module.device.mqtt.Rfc8785Canonicalizer;
import com.xuejiai.aaf.module.device.repository.DeviceMessageRepository;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;
import com.xuejiai.aaf.module.device.scenario.DeviceScenarioRegistry;

/** 设备下行消息业务入口实现。消息与 outbox 在同一事务中持久化。 */
@Service
public class DefaultDeviceMessageApi implements DeviceMessageApi {

    /** 配置下发是通用能力，任何场景都可使用，无需在场景中声明。 */
    static final String CONFIG_CAPABILITY = "config.update";

    private final DeviceRepository deviceRepository;
    private final DeviceMessageRepository messageRepository;
    private final DeviceMessageStateService stateService;
    private final DeviceScenarioRegistry scenarioRegistry;
    private final Rfc8785Canonicalizer canonicalizer;

    public DefaultDeviceMessageApi(
            DeviceRepository deviceRepository,
            DeviceMessageRepository messageRepository,
            DeviceMessageStateService stateService,
            DeviceScenarioRegistry scenarioRegistry,
            Rfc8785Canonicalizer canonicalizer) {
        this.deviceRepository = deviceRepository;
        this.messageRepository = messageRepository;
        this.stateService = stateService;
        this.scenarioRegistry = scenarioRegistry;
        this.canonicalizer = canonicalizer;
    }

    @Override
    @Transactional
    public DeviceMessageRef enqueue(DeviceMessageCommand command) {
        validate(command);
        var targetDeviceId = resolveDeviceId(command);
        var device =
                deviceRepository
                        .findLockedById(targetDeviceId)
                        .filter(
                                value ->
                                        DeviceStatusEnum.BOUND.getCode().equals(value.getStatus())
                                                && value.getCredentialRevokedAt() == null)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCodeConstants.DEVICE_NOT_FOUND));
        requireSupportedCapability(device, command);
        var commandId = normalizedId(command.commandId(), "cmd_");
        var contentHash = contentHash(command, device.getDeviceKey());
        var existing = messageRepository.findByCommandId(commandId);
        if (existing.isPresent()) {
            if (!MessageDigest.isEqual(
                    existing.get().getPayloadHash().getBytes(StandardCharsets.US_ASCII),
                    contentHash.getBytes(StandardCharsets.US_ASCII))) {
                throw new BusinessException(ErrorCodeConstants.DEVICE_MESSAGE_IDEMPOTENCY_CONFLICT);
            }
            return toRef(existing.get());
        }

        var now = LocalDateTime.now();
        var message = new DeviceMessage();
        message.setDeviceId(device.getId());
        message.setCommandId(commandId);
        message.setMessageId(normalizedId(null, "msg_"));
        message.setDirection(command.direction());
        message.setCapability(command.capability());
        message.setAction(command.action());
        message.setPayload(JsonUtils.toJsonString(command.arguments()));
        message.setStatus(DeviceMessageStatusEnum.QUEUED.getCode());
        message.setCorrelationId(command.correlationId());
        message.setTraceId(command.traceId());
        message.setPayloadHash(contentHash);
        message.setSequenceNo(messageRepository.findMaxSequenceByDeviceId(device.getId()) + 1);
        message.setIssuedAt(now);
        message.setExpiresAt(command.expiresAt());
        message.setOwnerId(device.getOwnerUserId());
        message.setOrgId(device.getOrgId());
        message.setWorkspaceId(device.getWorkspaceId());
        return toRef(messageRepository.save(message));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceMessageView> findByCommandId(String commandId) {
        return messageRepository.findByCommandId(commandId).map(DefaultDeviceMessageApi::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceMessageView> findByMessageId(String messageId) {
        return messageRepository.findByMessageId(messageId).map(DefaultDeviceMessageApi::toView);
    }

    @Override
    @Transactional
    public List<DeviceMessageView> listByCorrelationPrefix(
            String correlationPrefix, Collection<String> statuses) {
        return messageRepository
                .findTop100ByCorrelationIdStartingWithAndStatusInOrderByIssuedAtAsc(
                        correlationPrefix, statuses)
                .stream()
                .map(DefaultDeviceMessageApi::toView)
                .toList();
    }

    @Override
    @Transactional
    public List<DeviceMessageView> listExpiredWithoutResult(
            String capability,
            String correlationPrefix,
            LocalDateTime expiredBefore,
            Collection<String> statuses) {
        return messageRepository
                .findTop100ByCapabilityAndCorrelationIdStartingWithAndExpiresAtBeforeAndStatusInAndResultIsNullOrderByExpiresAtAsc(
                        capability, correlationPrefix, expiredBefore, statuses)
                .stream()
                .map(DefaultDeviceMessageApi::toView)
                .toList();
    }

    @Override
    @Transactional
    public void markCancelled(Long messageId, String resultJson) {
        var message = requireMessage(messageId);
        stateService.transition(message, DeviceMessageStatusEnum.CANCELLED);
        message.setCompletedAt(LocalDateTime.now());
        message.setResult(resultJson);
        messageRepository.save(message);
    }

    @Override
    @Transactional
    public void markExpired(Long messageId, String failureReason, String resultJson) {
        var message = requireMessage(messageId);
        stateService.transition(message, DeviceMessageStatusEnum.EXPIRED);
        if (failureReason != null) {
            message.setFailureReason(failureReason);
        }
        if (resultJson != null) {
            message.setResult(resultJson);
        }
        messageRepository.save(message);
    }

    private DeviceMessage requireMessage(Long messageId) {
        return messageRepository
                .findById(messageId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCodeConstants.DEVICE_MESSAGE_INVALID));
    }

    private Long resolveDeviceId(DeviceMessageCommand command) {
        if (command.deviceId() != null) {
            return command.deviceId();
        }
        var scenario = scenarioRegistry.require(command.scenarioKey());
        var deviceType = scenario.commandDeviceType();
        var found =
                deviceType == null
                        ? deviceRepository.findFirstByScenarioKeyAndSubjectIdAndStatusOrderByIdAsc(
                                command.scenarioKey(),
                                command.subjectId(),
                                DeviceStatusEnum.BOUND.getCode())
                        : deviceRepository
                                .findFirstByScenarioKeyAndSubjectIdAndDeviceTypeAndStatusOrderByIdAsc(
                                        command.scenarioKey(),
                                        command.subjectId(),
                                        deviceType,
                                        DeviceStatusEnum.BOUND.getCode());
        return found.map(Device::getId)
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.DEVICE_NOT_FOUND));
    }

    private void validate(DeviceMessageCommand command) {
        var byDevice = command != null && command.deviceId() != null;
        var hasScenario =
                command != null
                        && command.scenarioKey() != null
                        && !command.scenarioKey().isBlank();
        var hasSubject = command != null && command.subjectId() != null;
        var bySubject = hasScenario && hasSubject;
        if (command == null
                || byDevice && (hasScenario || hasSubject)
                || !byDevice && !bySubject
                || byDevice && command.deviceId() <= 0
                || bySubject && command.subjectId() <= 0
                || command.direction() == null
                || command.capability() == null
                || command.action() == null
                || command.arguments() == null
                || command.expiresAt() == null
                || !command.expiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_MESSAGE_INVALID);
        }
        try {
            DeviceMessageDirectionEnum.valueOf(command.direction());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_MESSAGE_INVALID);
        }
        canonicalizer.canonicalize(
                JsonUtils.convertValue(command.arguments(), tools.jackson.databind.JsonNode.class));
    }

    /** 能力白名单由设备所属场景声明；配置下发是通用能力，只允许 CONFIG 方向。 */
    private void requireSupportedCapability(Device device, DeviceMessageCommand command) {
        var direction = DeviceMessageDirectionEnum.valueOf(command.direction());
        var supported =
                switch (direction) {
                    case CONFIG -> CONFIG_CAPABILITY.equals(command.capability());
                    case COMMAND, CANCEL ->
                            scenarioRegistry
                                    .require(device.getScenarioKey())
                                    .commandCapabilities()
                                    .contains(command.capability());
                };
        if (!supported) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_MESSAGE_INVALID);
        }
    }

    private String contentHash(DeviceMessageCommand command, String deviceKey) {
        var content =
                Map.of(
                        "targetDeviceKey", deviceKey,
                        "direction", command.direction(),
                        "capability", command.capability(),
                        "action", command.action(),
                        "arguments", command.arguments(),
                        "expiresAt", command.expiresAt().toString());
        try {
            var canonical =
                    canonicalizer.canonicalize(
                            JsonUtils.convertValue(content, tools.jackson.databind.JsonNode.class));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static String normalizedId(String value, String prefix) {
        if (value == null || value.isBlank()) {
            return prefix + UUID.randomUUID().toString().replace("-", "");
        }
        if (value.length() > 100) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_MESSAGE_INVALID);
        }
        return value;
    }

    private static DeviceMessageRef toRef(DeviceMessage message) {
        return new DeviceMessageRef(
                message.getId(),
                message.getCommandId(),
                message.getMessageId(),
                message.getStatus(),
                message.getExpiresAt());
    }

    private static DeviceMessageView toView(DeviceMessage message) {
        return new DeviceMessageView(
                message.getId(),
                message.getDeviceId(),
                message.getCommandId(),
                message.getMessageId(),
                message.getCapability(),
                message.getCorrelationId(),
                message.getTraceId(),
                message.getStatus(),
                message.getPayload(),
                message.getAcceptedAt(),
                message.getResult(),
                message.getFailureReason());
    }
}
