package com.xuejiai.aaf.module.elder.guard.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.common.enums.device.DeviceMessageDirectionEnum;
import com.xuejiai.aaf.common.enums.device.DeviceMessageStatusEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationRecordResultEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationRecordSourceEnum;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessage;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.framework.task.DistributedLock;
import com.xuejiai.aaf.module.device.api.DeviceMessageApi;
import com.xuejiai.aaf.module.device.api.DeviceMessageApi.DeviceMessageView;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;
import com.xuejiai.aaf.module.elder.profile.api.MedicationApi;
import com.xuejiai.aaf.module.elder.profile.event.MedicationPlanDeactivatedEvent;
import com.xuejiai.aaf.module.elder.scenario.ElderDeviceScenario;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** 用药提醒的生成、设备结果回写、超时与撤销闭环。 */
@Service
@RequiredArgsConstructor
public class MedicationReminderService {

    private static final String CAPABILITY = "reminder.show";
    private static final String CORRELATION_PREFIX = "medication:";
    private static final String CONFIRM_WINDOW_KEY = "aaf.elder.alert.confirm-window-seconds";
    private static final String MISSED_THRESHOLD_KEY = "aaf.elder.medication.missed-threshold";
    private static final Collection<String> CLOSABLE_STATUSES =
            List.of(
                    DeviceMessageStatusEnum.QUEUED.getCode(),
                    DeviceMessageStatusEnum.DISPATCHED.getCode(),
                    DeviceMessageStatusEnum.ACCEPTED.getCode(),
                    DeviceMessageStatusEnum.DELIVERY_UNKNOWN.getCode(),
                    DeviceMessageStatusEnum.EXPIRED.getCode());
    private static final Collection<String> CANCELLABLE_STATUSES =
            List.of(
                    DeviceMessageStatusEnum.QUEUED.getCode(),
                    DeviceMessageStatusEnum.DISPATCHED.getCode(),
                    DeviceMessageStatusEnum.ACCEPTED.getCode(),
                    DeviceMessageStatusEnum.DELIVERY_UNKNOWN.getCode());

    private final MedicationApi medicationApi;
    private final DeviceMessageApi deviceMessageApi;
    private final ElderQueryApi elderQueryApi;
    private final InternalMessageSender messageSender;
    private final SystemConfigService configService;

    @OrgIgnore
    @Transactional
    @Scheduled(fixedDelay = 15_000, initialDelay = 10_000)
    @DistributedLock(key = "elder-medication-reminder-scan", ttlSeconds = 120)
    public void scan() {
        var now = Instant.now();
        enqueueDue(now);
        closeExpired(now);
    }

    /** 将已知的设备结果幂等回写 profile 域的服药事实。 */
    public void recordDeviceResult(DeviceMessageView message, String outcome, Instant recordedAt) {
        var payload = medicationPayload(message);
        if (payload == null) {
            return;
        }
        var result = medicationResult(outcome);
        if (result == null) {
            return;
        }
        var planId = requiredLong(payload, "planId");
        var scheduledAt = Instant.parse(requiredText(payload, "scheduledAt"));
        var zoneId = ZoneId.of(requiredText(payload, "zoneId"));
        medicationApi.recordResult(
                new MedicationApi.RecordMedicationResult(
                        planId,
                        scheduledAt,
                        zoneId,
                        result,
                        MedicationRecordSourceEnum.DEVICE.getCode(),
                        recordedAt == null ? Instant.now() : recordedAt,
                        sourceReference(message, outcome)));
        notifyIfThresholdReached(planId, requiredLong(payload, "elderId"), result);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPlanDeactivated(MedicationPlanDeactivatedEvent event) {
        var prefix = CORRELATION_PREFIX + event.planId() + ":";
        var messages = deviceMessageApi.listByCorrelationPrefix(prefix, CANCELLABLE_STATUSES);
        var now = LocalDateTime.now();
        for (var message : messages) {
            deviceMessageApi.enqueue(
                    new DeviceMessageApi.DeviceMessageCommand(
                            message.deviceId(),
                            null,
                            null,
                            "medication-cancel:" + message.commandId(),
                            DeviceMessageDirectionEnum.CANCEL.getCode(),
                            CAPABILITY,
                            "cancel",
                            Map.of(
                                    "commandId", message.commandId(),
                                    "messageId", message.messageId()),
                            now.plusMinutes(5),
                            "medication-cancel:" + event.planId(),
                            message.traceId()));
            deviceMessageApi.markCancelled(
                    message.id(),
                    JsonUtils.toJsonString(Map.of("reason", "MEDICATION_PLAN_DEACTIVATED")));
        }
    }

    private void enqueueDue(Instant now) {
        var confirmWindow = confirmWindowSeconds();
        var zoneId = ZoneId.systemDefault();
        var duePlans = medicationApi.listDuePlans(now.minusSeconds(30), now.plusSeconds(5), zoneId);
        for (var plan : duePlans) {
            var expiresAt = plan.scheduledAt().plusSeconds(confirmWindow);
            if (!expiresAt.isAfter(now)) {
                continue;
            }
            var scheduleKey = plan.planId() + ":" + plan.scheduledAt().toEpochMilli();
            deviceMessageApi.enqueue(
                    new DeviceMessageApi.DeviceMessageCommand(
                            null,
                            ElderDeviceScenario.KEY,
                            plan.elderId(),
                            "medication-reminder:" + scheduleKey,
                            DeviceMessageDirectionEnum.COMMAND.getCode(),
                            CAPABILITY,
                            "show",
                            Map.of(
                                    "reminderType", "MEDICATION",
                                    "planId", plan.planId(),
                                    "elderId", plan.elderId(),
                                    "medicationName", plan.medicationName(),
                                    "dosageInstructions", plan.dosageInstructions(),
                                    "scheduledAt", plan.scheduledAt().toString(),
                                    "zoneId", plan.zoneId().getId()),
                            LocalDateTime.ofInstant(expiresAt, zoneId),
                            CORRELATION_PREFIX + scheduleKey,
                            null));
        }
    }

    private void closeExpired(Instant now) {
        var localNow = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
        var messages =
                deviceMessageApi.listExpiredWithoutResult(
                        CAPABILITY, CORRELATION_PREFIX, localNow, CLOSABLE_STATUSES);
        for (var message : messages) {
            var payload = medicationPayload(message);
            if (payload == null) {
                deviceMessageApi.markExpired(message.id(), null, null);
                continue;
            }
            if (message.acceptedAt() == null) {
                deviceMessageApi.markExpired(
                        message.id(),
                        "提醒过期且设备未确认，记录为漏提醒",
                        JsonUtils.toJsonString(Map.of("reminderOutcome", "MISSED_DELIVERY")));
                continue;
            }
            var planId = requiredLong(payload, "planId");
            var elderId = requiredLong(payload, "elderId");
            medicationApi.recordResult(
                    new MedicationApi.RecordMedicationResult(
                            planId,
                            Instant.parse(requiredText(payload, "scheduledAt")),
                            ZoneId.of(requiredText(payload, "zoneId")),
                            MedicationRecordResultEnum.MISSED.getCode(),
                            MedicationRecordSourceEnum.SYSTEM.getCode(),
                            now,
                            message.messageId()));
            deviceMessageApi.markExpired(
                    message.id(),
                    "设备已接受提醒但确认窗口内无服药结果",
                    JsonUtils.toJsonString(Map.of("medicationOutcome", "MISSED")));
            notifyIfThresholdReached(planId, elderId, MedicationRecordResultEnum.MISSED.getCode());
        }
    }

    private void notifyIfThresholdReached(Long planId, Long elderId, String result) {
        if (!MedicationRecordResultEnum.MISSED.getCode().equals(result)) {
            return;
        }
        var threshold = Math.max(1, configService.getInteger(MISSED_THRESHOLD_KEY, 2));
        if (medicationApi.countConsecutiveMissed(planId) != threshold) {
            return;
        }
        var userIds =
                elderQueryApi.listContacts(elderId).stream()
                        .map(ElderQueryApi.ContactSnapshot::userId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
        afterCommit(
                () ->
                        userIds.forEach(
                                userId ->
                                        messageSender.send(
                                                InternalMessage.builder()
                                                        .userId(userId)
                                                        .type("elder-medication")
                                                        .title("长者连续漏服提醒")
                                                        .body("用药计划连续漏服达到提醒阈值，请登录查看并及时联系长者。")
                                                        .entityType("elder.medication.plan")
                                                        .entityId(planId)
                                                        .build())));
    }

    private Map<String, Object> medicationPayload(DeviceMessageView message) {
        if (!CAPABILITY.equals(message.capability())
                || message.correlationId() == null
                || !message.correlationId().startsWith(CORRELATION_PREFIX)) {
            return null;
        }
        var payload =
                JsonUtils.parseObject(
                        message.payload(), new TypeReference<Map<String, Object>>() {});
        return "MEDICATION".equals(payload.get("reminderType")) ? payload : null;
    }

    private String medicationResult(String outcome) {
        if (outcome == null) {
            return null;
        }
        return switch (outcome) {
            case "TAKEN" -> MedicationRecordResultEnum.TAKEN.getCode();
            case "SKIPPED", "LATER" -> MedicationRecordResultEnum.SKIPPED.getCode();
            case "NO_RESPONSE" -> MedicationRecordResultEnum.MISSED.getCode();
            default -> null;
        };
    }

    private int confirmWindowSeconds() {
        return Math.max(1, configService.getInteger(CONFIRM_WINDOW_KEY, 60));
    }

    private static Long requiredLong(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("用药提醒载荷缺少字段: " + key);
    }

    private static String requiredText(Map<String, Object> payload, String key) {
        var value = payload.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalArgumentException("用药提醒载荷缺少字段: " + key);
    }

    private static String sourceReference(DeviceMessageView message, String outcome) {
        var value = message.messageId() + ":" + outcome;
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
            return;
        }
        action.run();
    }
}
