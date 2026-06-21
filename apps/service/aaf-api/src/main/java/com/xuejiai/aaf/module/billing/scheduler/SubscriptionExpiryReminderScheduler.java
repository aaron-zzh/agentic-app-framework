package com.xuejiai.aaf.module.billing.scheduler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订阅到期提醒调度器（每日 09:00）。
 *
 * <p>扫描 end_at 在 {@code member.expiry_reminder_days}（默认 7 天）内的 ACTIVE 订阅，发站内通知。
 *
 * <p>幂等：用 {@code last_reminder_at > start_at} 判定本周期已发过，避免重复通知；订阅 start_at 仅在新购/升级时刷新，
 * 因此同一计费周期最多发一次提醒。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryReminderScheduler {

    /** 通知类型（与前端文案 / NotificationType 联动）。 */
    private static final String NOTIFICATION_TYPE = "SUBSCRIPTION_EXPIRY_REMINDER";

    /** 默认提醒提前天数（兜底：配置缺失时使用）。 */
    private static final int DEFAULT_REMINDER_DAYS = 7;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendReminders() {
        int reminderDays =
                systemConfigService.getInteger(
                        SysConfigKeys.Member.EXPIRY_REMINDER_DAYS, DEFAULT_REMINDER_DAYS);
        var now = LocalDateTime.now();
        var threshold = now.plusDays(reminderDays);

        var subs =
                subscriptionRepository.findByStatusAndEndAtIsNotNullAndEndAtLessThanEqual(
                        SubscriptionStatusEnum.ACTIVE.getCode(), threshold);

        int sent = 0;
        for (var sub : subs) {
            try {
                if (sendReminderIfNeeded(sub, now)) {
                    sent++;
                }
            } catch (Exception e) {
                log.warn(
                        "[SubscriptionExpiryReminderScheduler] 发送失败: subId={}, userId={}, err={}",
                        sub.getId(),
                        sub.getUserId(),
                        e.getMessage());
            }
        }
        log.info("[SubscriptionExpiryReminderScheduler] 到期提醒发送 {} 条", sent);
    }

    /**
     * 单个订阅处理，{@code package private} 便于单元测试。
     *
     * @return 是否实际发送
     */
    boolean sendReminderIfNeeded(Subscription sub, LocalDateTime now) {
        // 幂等：本周期已发过则跳过
        if (sub.getLastReminderAt() != null
                && sub.getStartAt() != null
                && sub.getLastReminderAt().isAfter(sub.getStartAt())) {
            return false;
        }

        var plan = planRepository.findById(sub.getPlanId()).orElse(null);
        String planName = plan != null ? plan.getName() : "当前订阅";
        String endDate = sub.getEndAt() != null ? sub.getEndAt().format(DATE_FORMATTER) : "未知";
        String title = "订阅即将到期";
        String body = String.format("您的「%s」将于 %s 到期，请及时续订以保留当前权益。", planName, endDate);

        notificationService.send(
                sub.getUserId(),
                NOTIFICATION_TYPE,
                title,
                body,
                "/settings/subscription",
                "SUBSCRIPTION",
                sub.getId());

        sub.setLastReminderAt(now);
        subscriptionRepository.save(sub);
        return true;
    }
}
