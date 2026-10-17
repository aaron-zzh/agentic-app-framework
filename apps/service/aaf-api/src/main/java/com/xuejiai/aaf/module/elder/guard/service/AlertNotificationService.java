package com.xuejiai.aaf.module.elder.guard.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.common.enums.elder.AlertHandlingChannelEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingStatusEnum;
import com.xuejiai.aaf.common.enums.elder.AlertHandlingTypeEnum;
import com.xuejiai.aaf.common.enums.elder.AlertLevelEnum;
import com.xuejiai.aaf.common.enums.elder.AlertRecipientTypeEnum;
import com.xuejiai.aaf.common.enums.elder.AlertTypeEnum;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessage;
import com.xuejiai.aaf.framework.messaging.internal.InternalMessageSender;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.task.AsyncQueueTaskService;
import com.xuejiai.aaf.framework.task.queue.TaskHandler;
import com.xuejiai.aaf.module.elder.guard.config.AlertNotificationProperties;
import com.xuejiai.aaf.module.elder.guard.domain.AlertEvent;
import com.xuejiai.aaf.module.elder.guard.event.AlertTriggeredEvent;
import com.xuejiai.aaf.module.elder.guard.repository.AlertEventRepository;
import com.xuejiai.aaf.module.elder.guard.service.AlertHandlingService.HandlingRecord;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;
import com.xuejiai.aaf.module.system.role.service.RoleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 预警通知异步任务：渠道相互隔离，每次发送结果均写入预警处理记录。 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AlertNotificationProperties.class)
public class AlertNotificationService implements TaskHandler {

    public static final String TASK_TYPE = "ELDER_ALERT_NOTIFY";

    private static final String DUTY_ROLE_CODE = "elder_duty";
    private static final int TASK_PRIORITY = 1;
    private static final DateTimeFormatter ALERT_TIME_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AsyncQueueTaskService asyncTaskService;
    private final AlertEventRepository alertRepository;
    private final AlertHandlingService handlingService;
    private final ElderQueryApi elderQueryApi;
    private final RoleService roleService;
    private final InternalMessageSender internalMessageSender;
    private final MessageService messageService;
    private final PermissionExecutionService permissionExecutionService;
    private final AlertNotificationProperties properties;

    /** 预警状态事务提交后再创建持久化异步任务，回滚的预警不会发送通知。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertTriggered(AlertTriggeredEvent event) {
        var alert =
                alertRepository
                        .findById(event.alertId())
                        .orElseThrow(
                                () -> new IllegalStateException("待通知预警不存在: " + event.alertId()));
        var payload =
                new AlertNotificationPayload(
                        event.alertId(),
                        event.elderId(),
                        event.ownerUserId(),
                        event.alertType(),
                        event.level(),
                        event.occurredAt(),
                        alert.getOrgId(),
                        alert.getWorkspaceId());
        validatePayload(payload);
        asyncTaskService.submit(
                TASK_TYPE,
                JsonUtils.toJsonString(payload),
                TASK_PRIORITY,
                payload.ownerUserId(),
                payload.orgId(),
                payload.workspaceId());
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public String handle(String taskId, String payloadJson) {
        var payload = JsonUtils.parseObject(payloadJson, AlertNotificationPayload.class);
        validatePayload(payload);
        return permissionExecutionService.runAsOwner(
                payload.ownerUserId(),
                "elder-alert-notify-queue",
                () -> runInOrgContext(payload, () -> notifyChannels(payload)));
    }

    private String notifyChannels(AlertNotificationPayload payload) {
        var alert =
                alertRepository
                        .findById(payload.alertId())
                        .orElseThrow(
                                () -> new IllegalStateException("待通知预警不存在: " + payload.alertId()));
        var elder = elderQueryApi.requireOverview(payload.elderId());
        var elderTitle =
                elder.preferredName() == null || elder.preferredName().isBlank()
                        ? "该长者"
                        : elder.preferredName();
        var subject = "养老预警通知 #%d".formatted(payload.alertId());
        var content = content(payload, elderTitle);

        notifyDutyUsers(alert, subject, content);
        notifyDingtalk(alert, subject, content);
        notifyContacts(alert, subject, content);
        if (properties.sms().enabled()) {
            log.warn("养老预警短信开关已开启，但本期未配置审核模板，不发送短信: alertId={}", payload.alertId());
        }
        return JsonUtils.toJsonString(new AlertNotificationResult(payload.alertId(), "PROCESSED"));
    }

    private void notifyDutyUsers(AlertEvent alert, String subject, String content) {
        var dutyUserIds = roleService.listActiveUserIdsByCode(DUTY_ROLE_CODE);
        if (dutyUserIds.isEmpty()) {
            appendResult(
                    alert,
                    AlertHandlingChannelEnum.INTERNAL,
                    AlertHandlingStatusEnum.FAILED,
                    AlertRecipientTypeEnum.DUTY_USER,
                    null,
                    "未找到启用的运营值班账号",
                    "运营值班角色没有启用账号");
            return;
        }
        dutyUserIds.forEach(
                userId ->
                        sendInternal(
                                alert,
                                userId,
                                null,
                                AlertRecipientTypeEnum.DUTY_USER,
                                subject,
                                content));
    }

    private void notifyDingtalk(AlertEvent alert, String subject, String content) {
        try {
            messageService.sendSync(
                    MessageRequest.direct(
                            MessageChannel.DINGTALK, subject, markdown(content), List.of("all")));
            appendResult(
                    alert,
                    AlertHandlingChannelEnum.DINGTALK,
                    AlertHandlingStatusEnum.SUCCESS,
                    AlertRecipientTypeEnum.DUTY_USER,
                    null,
                    "钉钉值班群通知已发送",
                    null);
        } catch (RuntimeException exception) {
            appendResult(
                    alert,
                    AlertHandlingChannelEnum.DINGTALK,
                    AlertHandlingStatusEnum.FAILED,
                    AlertRecipientTypeEnum.DUTY_USER,
                    null,
                    "钉钉值班群通知发送失败",
                    safeError(exception));
        }
    }

    private void notifyContacts(AlertEvent alert, String subject, String content) {
        for (var contact : elderQueryApi.listContacts(alert.getElderId())) {
            if (contact.userId() == null) {
                appendResult(
                        alert,
                        AlertHandlingChannelEnum.INTERNAL,
                        AlertHandlingStatusEnum.SKIPPED,
                        AlertRecipientTypeEnum.CONTACT,
                        contact.id(),
                        "联系人未绑定 AAF 账号，跳过站内信",
                        null);
                continue;
            }
            sendInternal(
                    alert,
                    contact.userId(),
                    contact.id(),
                    AlertRecipientTypeEnum.CONTACT,
                    subject,
                    content);
        }
    }

    private void sendInternal(
            AlertEvent alert,
            Long userId,
            Long recipientId,
            AlertRecipientTypeEnum recipientType,
            String subject,
            String content) {
        try {
            internalMessageSender.send(
                    InternalMessage.builder()
                            .userId(userId)
                            .type("elder-alert")
                            .title(subject)
                            .body(content)
                            .relatedUrl("/module/elder-alert")
                            .entityType("elder.guard.alert")
                            .entityId(alert.getId())
                            .build());
            appendResult(
                    alert,
                    AlertHandlingChannelEnum.INTERNAL,
                    AlertHandlingStatusEnum.SUCCESS,
                    recipientType,
                    recipientId == null ? userId : recipientId,
                    "站内信已发送",
                    null);
        } catch (RuntimeException exception) {
            appendResult(
                    alert,
                    AlertHandlingChannelEnum.INTERNAL,
                    AlertHandlingStatusEnum.FAILED,
                    recipientType,
                    recipientId == null ? userId : recipientId,
                    "站内信发送失败",
                    safeError(exception));
        }
    }

    private void appendResult(
            AlertEvent alert,
            AlertHandlingChannelEnum channel,
            AlertHandlingStatusEnum status,
            AlertRecipientTypeEnum recipientType,
            Long recipientId,
            String detail,
            String errorMessage) {
        handlingService.append(
                alert,
                new HandlingRecord(
                        AlertHandlingTypeEnum.NOTIFICATION.getCode(),
                        channel.getCode(),
                        status.getCode(),
                        recipientType.getCode(),
                        recipientId,
                        null,
                        null,
                        detail,
                        errorMessage,
                        LocalDateTime.now(ZoneOffset.UTC)));
    }

    private String content(AlertNotificationPayload payload, String elderTitle) {
        return """
                预警编号：%d
                预警类型：%s
                预警级别：%s
                预警时间：%s
                长者称呼：%s
                """
                .formatted(
                        payload.alertId(),
                        AlertTypeEnum.valueOf(payload.alertType()).getLabel(),
                        AlertLevelEnum.valueOf(payload.level()).getLabel(),
                        payload.occurredAt().atOffset(ZoneOffset.UTC).format(ALERT_TIME_FORMAT),
                        elderTitle)
                .stripTrailing();
    }

    private String markdown(String content) {
        return "**养老预警通知**\n\n> " + content.replace("\n", "  \n> ");
    }

    private String safeError(RuntimeException exception) {
        return "发送失败：" + exception.getClass().getSimpleName();
    }

    private <T> T runInOrgContext(AlertNotificationPayload payload, Supplier<T> action) {
        var previousOrgId = OrgContext.getCurrentOrgId();
        var previousWorkspaceId = OrgContext.getCurrentWorkspaceId();
        OrgContext.setCurrentOrgId(payload.orgId());
        OrgContext.setCurrentWorkspaceId(payload.workspaceId());
        try {
            return action.get();
        } finally {
            OrgContext.setCurrentOrgId(previousOrgId);
            OrgContext.setCurrentWorkspaceId(previousWorkspaceId);
        }
    }

    private void validatePayload(AlertNotificationPayload payload) {
        if (payload == null
                || payload.alertId() == null
                || payload.elderId() == null
                || payload.ownerUserId() == null
                || payload.ownerUserId() <= 0
                || payload.alertType() == null
                || payload.level() == null
                || payload.occurredAt() == null) {
            throw new IllegalArgumentException("养老预警通知任务载荷无效");
        }
        AlertTypeEnum.valueOf(payload.alertType());
        AlertLevelEnum.valueOf(payload.level());
    }

    record AlertNotificationPayload(
            Long alertId,
            Long elderId,
            Long ownerUserId,
            String alertType,
            String level,
            LocalDateTime occurredAt,
            Long orgId,
            Long workspaceId) {}

    record AlertNotificationResult(Long alertId, String status) {}
}
