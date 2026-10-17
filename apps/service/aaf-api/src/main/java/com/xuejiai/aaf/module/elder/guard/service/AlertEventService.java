package com.xuejiai.aaf.module.elder.guard.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.guard.ErrorCodeConstants.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.device.DeviceCameraStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceOnlineStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.common.enums.device.DeviceZoneEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingChannelEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingStatusEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingTypeEnum;
import com.xuejiai.aaf.common.enums.elder.AlertLevelEnum;
import com.xuejiai.aaf.common.enums.elder.AlertRecipientTypeEnum;
import com.xuejiai.aaf.common.enums.elder.AlertStatusEnum;
import com.xuejiai.aaf.common.enums.elder.AlertTypeEnum;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.framework.task.DistributedLock;
import com.xuejiai.aaf.module.device.api.DeviceInboundHandler;
import com.xuejiai.aaf.module.device.api.DevicePresenceChangedEvent;
import com.xuejiai.aaf.module.device.api.DeviceQueryApi;
import com.xuejiai.aaf.module.device.api.DeviceQueryApi.DeviceView;
import com.xuejiai.aaf.module.elder.guard.AlertEventResource;
import com.xuejiai.aaf.module.elder.guard.domain.AlertEvent;
import com.xuejiai.aaf.module.elder.guard.event.AlertTriggeredEvent;
import com.xuejiai.aaf.module.elder.guard.mapper.AlertEventConvert;
import com.xuejiai.aaf.module.elder.guard.repository.AlertEventRepository;
import com.xuejiai.aaf.module.elder.guard.service.AlertHandlingService.HandlingRecord;
import com.xuejiai.aaf.module.elder.guard.vo.AlertEventCreateDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertEventPageDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertEventUpdateDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertEventVO;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;
import com.xuejiai.aaf.module.elder.scenario.ElderDeviceScenario;

/** 预警事件管理与设备语义事件判定服务。 */
@Service
@Transactional(readOnly = true)
public class AlertEventService
        extends BaseCrudService<
                AlertEvent,
                AlertEventVO,
                AlertEventCreateDTO,
                AlertEventUpdateDTO,
                AlertEventPageDTO>
        implements DeviceInboundHandler {

    private static final String CONFIG_CONFIRM_WINDOW = "aaf.elder.alert.confirm-window-seconds";
    private static final String CONFIG_LATE_AFTER = "aaf.elder.alert.late-after-seconds";
    private static final String CONFIG_NO_ACTIVITY_HOURS = "aaf.elder.alert.no-activity-hours";
    private static final String CONFIG_AWAKE_START = "aaf.elder.alert.awake-start";
    private static final String CONFIG_AWAKE_END = "aaf.elder.alert.awake-end";
    private static final Set<String> TERMINAL_STATUSES =
            Set.of(
                    AlertStatusEnum.RESOLVED.getCode(),
                    AlertStatusEnum.FALSE_ALARM.getCode(),
                    AlertStatusEnum.CANCELED.getCode());
    private static final Map<String, Set<String>> STATUS_TRANSITIONS =
            Map.of(
                    AlertStatusEnum.TRIGGERED.getCode(),
                    Set.of(AlertStatusEnum.NOTIFYING.getCode(), AlertStatusEnum.CANCELED.getCode()),
                    AlertStatusEnum.NOTIFYING.getCode(),
                    Set.of(AlertStatusEnum.ACKED.getCode()),
                    AlertStatusEnum.ACKED.getCode(),
                    Set.of(
                            AlertStatusEnum.RESOLVED.getCode(),
                            AlertStatusEnum.FALSE_ALARM.getCode()));

    private final AlertEventRepository repository;
    private final DeviceQueryApi deviceQueryApi;
    private final AlertHandlingService handlingService;
    private final ElderQueryApi elderQueryApi;
    private final OperatorContext operatorContext;
    private final SystemConfigService configService;
    private final ApplicationEventPublisher eventPublisher;

    public AlertEventService(
            AlertEventRepository repository,
            DeviceQueryApi deviceQueryApi,
            AlertHandlingService handlingService,
            ElderQueryApi elderQueryApi,
            OperatorContext operatorContext,
            SystemConfigService configService,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.deviceQueryApi = deviceQueryApi;
        this.handlingService = handlingService;
        this.elderQueryApi = elderQueryApi;
        this.operatorContext = operatorContext;
        this.configService = configService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected AlertEventRepository getRepository() {
        return repository;
    }

    @Override
    protected AlertEventVO toVO(AlertEvent entity) {
        return new AlertEventVO(
                entity.getId(),
                entity.getVersion(),
                entity.getDeviceId(),
                entity.getElderId(),
                entity.getEventId(),
                entity.getAlertType(),
                entity.getLevel(),
                entity.getStatus(),
                entity.getZone(),
                entity.getConfidence(),
                entity.getPayload(),
                entity.getDeviceOccurredAt(),
                entity.getReceivedAt(),
                entity.getLate(),
                entity.getNextActionAt(),
                entity.getClaimedBy(),
                entity.getClaimedAt(),
                entity.getAcknowledgedAt(),
                entity.getResolvedAt(),
                entity.getRemark(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                null,
                null,
                null,
                null,
                null);
    }

    @Override
    protected AlertEvent toEntity(AlertEventCreateDTO request) {
        var entity = AlertEventConvert.INSTANCE.toEntity(request);
        if (entity.getReceivedAt() == null) {
            entity.setReceivedAt(now());
        }
        validate(entity);
        return entity;
    }

    /** 标准更新只承载处理结论；认领与代记已知晓必须走具名命令。 */
    @Override
    protected void updateEntity(AlertEvent entity, AlertEventUpdateDTO request) {
        requireExpectedVersion(entity, request.expectedVersion());
        if (request.status().isAbsent() || request.status().isNullValue()) {
            throw exception(ALERT_REQUEST_INVALID);
        }
        applyNullable(request.remark(), entity::setRemark);
        var target = request.status().valueOrNull();
        if (!Set.of(AlertStatusEnum.RESOLVED.getCode(), AlertStatusEnum.FALSE_ALARM.getCode())
                .contains(target)) {
            throw exception(ALERT_STATUS_TRANSITION_INVALID, entity.getStatus(), target);
        }
        if (entity.getRemark() == null || entity.getRemark().isBlank()) {
            throw exception(ALERT_REQUEST_INVALID);
        }
        transition(entity, target);
        entity.setResolvedAt(now());
        entity.setNextActionAt(null);
        appendManualAudit(
                entity, AlertHandlingTypeEnum.RESOLUTION.getCode(), null, entity.getRemark());
        entity.setVersion(currentVersion(entity) + 1);
    }

    @Override
    protected void beforeDelete(AlertEvent entity) {
        throw exception(ALERT_IMMUTABLE);
    }

    @Override
    protected Specification<AlertEvent> buildSpec(AlertEventPageDTO request) {
        return SpecificationBuilder.<AlertEvent>builder()
                .eqIfPresent("deviceId", request.getDeviceId())
                .eqIfPresent("elderId", request.getElderId())
                .eqIfPresent("alertType", request.getAlertType())
                .eqIfPresent("level", request.getLevel())
                .eqIfPresent("status", request.getStatus())
                .eqIfPresent("late", request.getLate())
                .eqIfPresent("claimedBy", request.getClaimedBy())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of();
    }

    /** 认领不改变预警状态，但写入认领人、时间与不可变审计。 */
    @Transactional
    public AlertEventVO claim(Long alertId) {
        var operatorId = currentOperatorId();
        var plan =
                new CustomUpdatePlan<AlertEvent, ClaimCommand, Void, AlertEventVO>(
                        AlertEventResource.COMMAND_CLAIM,
                        Set.of("version", "claimedBy", "claimedAt"),
                        (alert, command) -> requireClaimable(alert),
                        (alert, command) -> {
                            alert.setClaimedBy(command.operatorId());
                            alert.setClaimedAt(now());
                            alert.setVersion(currentVersion(alert) + 1);
                        },
                        (alert, command) -> {
                            appendManualAudit(
                                    alert, AlertHandlingTypeEnum.CLAIM.getCode(), null, "运营认领预警");
                            return null;
                        },
                        true,
                        (alert, command, ignored) -> {},
                        (alert, command, ignored) -> toVO(alert));
        return executeCustomUpdateCommand(alertId, new ClaimCommand(operatorId), plan);
    }

    /** 代记已知晓要求当前运营已认领；传入联系人时校验其属于该长者。 */
    @Transactional
    public AlertEventVO acknowledge(Long alertId, Long acknowledgedContactId) {
        var operatorId = currentOperatorId();
        var plan =
                new CustomUpdatePlan<AlertEvent, AcknowledgeCommand, Void, AlertEventVO>(
                        AlertEventResource.COMMAND_ACKNOWLEDGE,
                        Set.of("version", "status", "acknowledgedAt"),
                        (alert, command) -> requireAcknowledgable(alert, command),
                        (alert, command) -> {
                            transition(alert, AlertStatusEnum.ACKED.getCode());
                            alert.setAcknowledgedAt(now());
                            alert.setNextActionAt(null);
                            alert.setVersion(currentVersion(alert) + 1);
                        },
                        (alert, command) -> {
                            appendManualAudit(
                                    alert,
                                    AlertHandlingTypeEnum.ACKNOWLEDGEMENT.getCode(),
                                    command.acknowledgedContactId(),
                                    "运营代记联系人已知晓");
                            return null;
                        },
                        true,
                        (alert, command, ignored) -> {},
                        (alert, command, ignored) -> toVO(alert));
        return executeCustomUpdateCommand(
                alertId, new AcknowledgeCommand(operatorId, acknowledgedContactId), plan);
    }

    @Override
    public boolean supports(InboundType type) {
        return type == InboundType.ALERT;
    }

    @Override
    public boolean supportsScenario(String scenarioKey) {
        return ElderDeviceScenario.KEY.equals(scenarioKey);
    }

    /** 处理 up/alert；设备事件以 (deviceId,eventId) 幂等。 */
    @Override
    @Transactional
    public void handle(DeviceInboundMessage inbound) {
        var device = requireLockedDevice(inbound.deviceId());
        var eventType = text(inbound.payload().get("type"));
        if ("CONFIRMED_OK".equals(eventType)) {
            confirmSafe(inbound);
            return;
        }
        if (!AlertTypeEnum.FALL_SUSPECTED.getCode().equals(eventType)
                && !AlertTypeEnum.HELP_REQUEST.getCode().equals(eventType)) {
            throw exception(ALERT_REQUEST_INVALID);
        }
        if (repository
                .findByDeviceIdAndEventId(inbound.deviceId(), inbound.eventId())
                .isPresent()) {
            return;
        }
        var receivedInstant = Instant.now();
        var receivedAt = LocalDateTime.ofInstant(receivedInstant, ZoneOffset.UTC);
        var late =
                Duration.between(inbound.deviceOccurredAt(), receivedInstant).toSeconds()
                        > lateAfterSeconds();
        var alert = new AlertEvent();
        applyDeviceScope(alert, device);
        alert.setEventId(inbound.eventId());
        alert.setAlertType(eventType);
        alert.setLevel(AlertLevelEnum.EMERGENCY.getCode());
        alert.setStatus(AlertStatusEnum.TRIGGERED.getCode());
        alert.setZone(requireZone(inbound.payload().get("zone"), device.zone()));
        alert.setConfidence(confidence(inbound.payload().get("confidence")));
        alert.setPayload(semanticPayload(inbound.payload().get("payload")));
        alert.setDeviceOccurredAt(
                LocalDateTime.ofInstant(inbound.deviceOccurredAt(), ZoneOffset.UTC));
        alert.setReceivedAt(receivedAt);
        alert.setLate(late);
        if (AlertTypeEnum.FALL_SUSPECTED.getCode().equals(eventType) && !late) {
            alert.setNextActionAt(receivedAt.plusSeconds(confirmWindowSeconds()));
        }
        repository.save(alert);
        if (AlertTypeEnum.HELP_REQUEST.getCode().equals(eventType) || late) {
            enterNotifying(alert, receivedAt);
        }
    }

    /** 推进已到期的疑似跌倒确认窗口。 */
    @OrgIgnore
    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    @DistributedLock(key = "elder-alert-confirmation-scan", ttlSeconds = 4)
    @Transactional
    public void scanConfirmationWindows() {
        var current = now();
        for (var alertId : repository.findDueIds(AlertStatusEnum.TRIGGERED.getCode(), current)) {
            var alert = repository.findLockedById(alertId).orElse(null);
            if (alert != null
                    && AlertStatusEnum.TRIGGERED.getCode().equals(alert.getStatus())
                    && alert.getNextActionAt() != null
                    && !alert.getNextActionAt().isAfter(current)) {
                enterNotifying(alert, current);
            }
        }
    }

    /** 在线、摄像头可用、非隐私且处于清醒时段时判定长时间无活动。 */
    @OrgIgnore
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @DistributedLock(key = "elder-alert-no-activity-scan", ttlSeconds = 55)
    @Transactional
    public void scanNoActivity() {
        var current = now();
        if (!isAwakeTime(LocalTime.now())) {
            return;
        }
        var candidates = deviceQueryApi.listBoundOnline(ElderDeviceScenario.KEY);
        for (var candidate : candidates) {
            var device = requireLockedDevice(candidate.id());
            if (!eligibleForNoActivity(device, current)) {
                continue;
            }
            var eventId = "NO_ACTIVITY:%d:%s".formatted(device.id(), device.lastPersonSeenAt());
            createSystemAlert(
                    device,
                    eventId,
                    AlertTypeEnum.NO_ACTIVITY.getCode(),
                    AlertLevelEnum.HIGH.getCode(),
                    current);
        }
    }

    /** 设备首次进入离线态时立即生成高等级预警，并暂停后续无活动判定。 */
    @EventListener
    @Transactional
    public void onDevicePresenceChanged(DevicePresenceChangedEvent event) {
        if (!ElderDeviceScenario.KEY.equals(event.scenarioKey())
                || !DeviceOnlineStatusEnum.OFFLINE.getCode().equals(event.currentStatus())
                || DeviceOnlineStatusEnum.OFFLINE.getCode().equals(event.previousStatus())) {
            return;
        }
        var device = requireLockedDevice(event.deviceId());
        if (!DeviceOnlineStatusEnum.OFFLINE.getCode().equals(device.onlineStatus())) {
            return;
        }
        var eventId = "DEVICE_OFFLINE:%d:%s".formatted(device.id(), event.changedAt());
        createSystemAlert(
                device,
                eventId,
                AlertTypeEnum.DEVICE_OFFLINE.getCode(),
                AlertLevelEnum.HIGH.getCode(),
                event.changedAt());
    }

    private void confirmSafe(DeviceInboundMessage inbound) {
        if (inbound.correlationId() == null || inbound.correlationId().isBlank()) {
            throw exception(ALERT_REQUEST_INVALID);
        }
        var alert =
                repository
                        .findLockedByDeviceIdAndEventId(inbound.deviceId(), inbound.correlationId())
                        .orElseThrow(() -> exception(ALERT_NOT_FOUND));
        if (AlertStatusEnum.CANCELED.getCode().equals(alert.getStatus())) {
            return;
        }
        var current = now();
        if (alert.getNextActionAt() == null || !current.isBefore(alert.getNextActionAt())) {
            if (AlertStatusEnum.TRIGGERED.getCode().equals(alert.getStatus())) {
                enterNotifying(alert, current);
            }
            return;
        }
        transition(alert, AlertStatusEnum.CANCELED.getCode());
        alert.setNextActionAt(null);
        alert.setResolvedAt(current);
        alert.setVersion(currentVersion(alert) + 1);
        repository.save(alert);
    }

    private AlertEvent createSystemAlert(
            DeviceView device,
            String eventId,
            String alertType,
            String level,
            LocalDateTime occurredAt) {
        var existing = repository.findByDeviceIdAndEventId(device.id(), eventId).orElse(null);
        if (existing != null) {
            return existing;
        }
        var alert = new AlertEvent();
        applyDeviceScope(alert, device);
        alert.setEventId(eventId);
        alert.setAlertType(alertType);
        alert.setLevel(level);
        alert.setStatus(AlertStatusEnum.TRIGGERED.getCode());
        alert.setZone(device.zone());
        alert.setPayload("{}");
        alert.setDeviceOccurredAt(occurredAt);
        alert.setReceivedAt(now());
        alert.setLate(false);
        repository.save(alert);
        enterNotifying(alert, now());
        return alert;
    }

    private void enterNotifying(AlertEvent alert, LocalDateTime triggeredAt) {
        transition(alert, AlertStatusEnum.NOTIFYING.getCode());
        alert.setNextActionAt(null);
        alert.setVersion(currentVersion(alert) + 1);
        repository.save(alert);
        eventPublisher.publishEvent(
                new AlertTriggeredEvent(
                        alert.getId(),
                        alert.getDeviceId(),
                        alert.getElderId(),
                        alert.getOwnerId(),
                        alert.getAlertType(),
                        alert.getLevel(),
                        alert.getZone(),
                        alert.getDeviceOccurredAt(),
                        Boolean.TRUE.equals(alert.getLate()),
                        triggeredAt));
    }

    private void appendManualAudit(
            AlertEvent alert, String handlingType, Long acknowledgedContactId, String detail) {
        var operatorId = currentOperatorId();
        handlingService.append(
                alert,
                new HandlingRecord(
                        handlingType,
                        AlertHandlingChannelEnum.MANUAL.getCode(),
                        AlertHandlingStatusEnum.SUCCESS.getCode(),
                        acknowledgedContactId == null
                                ? null
                                : AlertRecipientTypeEnum.CONTACT.getCode(),
                        acknowledgedContactId,
                        operatorId,
                        acknowledgedContactId,
                        detail,
                        null,
                        now()));
    }

    private void requireClaimable(AlertEvent alert) {
        if (!AlertStatusEnum.NOTIFYING.getCode().equals(alert.getStatus())) {
            throw exception(
                    ALERT_STATUS_TRANSITION_INVALID,
                    alert.getStatus(),
                    AlertStatusEnum.NOTIFYING.getCode());
        }
        if (alert.getClaimedBy() != null) {
            throw exception(ALERT_ALREADY_CLAIMED);
        }
    }

    private void requireAcknowledgable(AlertEvent alert, AcknowledgeCommand command) {
        if (!AlertStatusEnum.NOTIFYING.getCode().equals(alert.getStatus())) {
            throw exception(
                    ALERT_STATUS_TRANSITION_INVALID,
                    alert.getStatus(),
                    AlertStatusEnum.ACKED.getCode());
        }
        if (!command.operatorId().equals(alert.getClaimedBy())) {
            throw exception(ALERT_CLAIM_REQUIRED);
        }
        if (command.acknowledgedContactId() != null
                && elderQueryApi.listContacts(alert.getElderId()).stream()
                        .noneMatch(
                                contact -> command.acknowledgedContactId().equals(contact.id()))) {
            throw exception(ALERT_CONTACT_INVALID);
        }
    }

    private boolean eligibleForNoActivity(DeviceView device, LocalDateTime current) {
        if (!DeviceStatusEnum.BOUND.getCode().equals(device.status())
                || !DeviceOnlineStatusEnum.ONLINE.getCode().equals(device.onlineStatus())
                || device.privacyMode()
                || !DeviceCameraStatusEnum.AVAILABLE.getCode().equals(device.cameraStatus())
                || device.lastPersonSeenAt() == null) {
            return false;
        }
        return !device.lastPersonSeenAt().plusHours(noActivityHours()).isAfter(current);
    }

    private boolean isAwakeTime(LocalTime current) {
        var start = LocalTime.parse(configService.getString(CONFIG_AWAKE_START, "07:00"));
        var end = LocalTime.parse(configService.getString(CONFIG_AWAKE_END, "22:00"));
        if (start.equals(end)) {
            return true;
        }
        return start.isBefore(end)
                ? !current.isBefore(start) && current.isBefore(end)
                : !current.isBefore(start) || current.isBefore(end);
    }

    private void applyDeviceScope(AlertEvent alert, DeviceView device) {
        alert.setDeviceId(device.id());
        alert.setElderId(device.subjectId());
        alert.setOrgId(device.orgId());
        alert.setWorkspaceId(device.workspaceId());
        alert.setOwnerId(device.ownerUserId());
    }

    private DeviceView requireLockedDevice(Long deviceId) {
        return deviceQueryApi.requireLocked(deviceId);
    }

    private String requireZone(Object value, String defaultZone) {
        var zone = value == null ? defaultZone : text(value);
        if (zone == null || Arrays.stream(DeviceZoneEnum.ARRAYS).noneMatch(zone::equals)) {
            throw exception(ALERT_REQUEST_INVALID);
        }
        return zone;
    }

    private BigDecimal confidence(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw exception(ALERT_REQUEST_INVALID);
        }
        var confidence = BigDecimal.valueOf(number.doubleValue());
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw exception(ALERT_REQUEST_INVALID);
        }
        return confidence;
    }

    private String semanticPayload(Object value) {
        if (value == null) {
            return "{}";
        }
        if (!(value instanceof Map<?, ?> payload) || containsMedia(payload)) {
            throw exception(ALERT_REQUEST_INVALID);
        }
        return JsonUtils.toJsonString(payload);
    }

    private boolean containsMedia(Map<?, ?> payload) {
        return payload.keySet().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(key -> key.toLowerCase(Locale.ROOT))
                .anyMatch(
                        key ->
                                key.contains("image")
                                        || key.contains("video")
                                        || key.contains("face"));
    }

    private void validate(AlertEvent entity) {
        if (entity.getDeviceId() == null
                || entity.getElderId() == null
                || entity.getEventId() == null
                || entity.getEventId().isBlank()
                || entity.getEventId().length() > 100
                || !allowed(entity.getAlertType(), AlertTypeEnum.ARRAYS)
                || !allowed(entity.getLevel(), AlertLevelEnum.ARRAYS)
                || !allowed(entity.getStatus(), AlertStatusEnum.ARRAYS)
                || !allowed(entity.getZone(), DeviceZoneEnum.ARRAYS)
                || entity.getDeviceOccurredAt() == null
                || entity.getReceivedAt() == null) {
            throw exception(ALERT_REQUEST_INVALID);
        }
    }

    private boolean allowed(String value, String[] values) {
        return value != null && Arrays.stream(values).anyMatch(value::equals);
    }

    private void transition(AlertEvent alert, String target) {
        var current = alert.getStatus();
        if (current == null
                || current.equals(target)
                || TERMINAL_STATUSES.contains(current)
                || !STATUS_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw exception(ALERT_STATUS_TRANSITION_INVALID, current, target);
        }
        alert.setStatus(target);
    }

    private void requireExpectedVersion(AlertEvent entity, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(entity)) {
            throw exception(ALERT_VERSION_CONFLICT);
        }
    }

    private int currentVersion(AlertEvent entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }

    private <T> void applyNullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) {
            setter.accept(patch.valueOrNull());
        }
    }

    private Long currentOperatorId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(
                        () ->
                                exception(
                                        com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                .UNAUTHORIZED));
    }

    private int confirmWindowSeconds() {
        return configService.getInteger(CONFIG_CONFIRM_WINDOW, 60);
    }

    private int lateAfterSeconds() {
        return configService.getInteger(CONFIG_LATE_AFTER, 60);
    }

    private int noActivityHours() {
        return configService.getInteger(CONFIG_NO_ACTIVITY_HOURS, 4);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private record ClaimCommand(Long operatorId) {}

    private record AcknowledgeCommand(Long operatorId, Long acknowledgedContactId) {}
}
