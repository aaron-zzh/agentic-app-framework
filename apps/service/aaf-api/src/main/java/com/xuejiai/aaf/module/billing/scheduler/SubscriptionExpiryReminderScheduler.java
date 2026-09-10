package com.xuejiai.aaf.module.billing.scheduler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.repository.MembershipCheckoutGuardRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 订阅到期提醒调度器（每日 09:00），提醒事实也遵循统一会员锁序。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryReminderScheduler {

    private static final String NOTIFICATION_TYPE = "SUBSCRIPTION_EXPIRY_REMINDER";
    private static final int DEFAULT_REMINDER_DAYS = 7;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final MembershipCheckoutGuardRepository checkoutGuardRepository;
    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;

    @OrgIgnore
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendReminders() {
        var reminderDays =
                systemConfigService.getInteger(
                        SysConfigKeys.Member.EXPIRY_REMINDER_DAYS, DEFAULT_REMINDER_DAYS);
        var now = LocalDateTime.now();
        var threshold = now.plusDays(reminderDays);
        var candidates =
                subscriptionRepository.findByStatusAndEndAtIsNotNullAndEndAtLessThanEqual(
                        SubscriptionStatusEnum.ACTIVE.getCode(), threshold);

        var sent = 0;
        for (var candidate : candidates) {
            try {
                if (sendReminderIfNeeded(candidate, now)) {
                    sent++;
                }
            } catch (Exception exception) {
                log.warn(
                        "[SubscriptionExpiryReminderScheduler] 发送失败: subId={}, userId={}, err={}",
                        candidate.getId(),
                        candidate.getUserId(),
                        exception.getMessage());
            }
        }
        log.info("[SubscriptionExpiryReminderScheduler] 到期提醒发送 {} 条", sent);
    }

    /** 单个候选先锁 guard 和当前 ACTIVE，再检查幂等并记录提醒事实。 */
    boolean sendReminderIfNeeded(Subscription candidate, LocalDateTime now) {
        checkoutGuardRepository.ensureGuard(candidate.getUserId());
        checkoutGuardRepository.lockGuard(candidate.getUserId());
        var subscription =
                subscriptionRepository
                        .findByUserIdAndStatusForUpdate(
                                candidate.getUserId(), SubscriptionStatusEnum.ACTIVE.getCode())
                        .orElse(null);
        if (subscription == null || !subscription.getId().equals(candidate.getId())) {
            return false;
        }
        if (subscription.getLastReminderAt() != null
                && subscription.getStartAt() != null
                && subscription.getLastReminderAt().isAfter(subscription.getStartAt())) {
            return false;
        }

        var plan = planRepository.findById(subscription.getPlanId()).orElse(null);
        var planName = plan != null ? plan.getName() : "当前订阅";
        var endDate =
                subscription.getEndAt() != null
                        ? subscription.getEndAt().format(DATE_FORMATTER)
                        : "未知";
        var title = "订阅即将到期";
        var body = "您的「%s」将于 %s 到期，请及时续订以保留当前权益。".formatted(planName, endDate);

        notificationService.send(
                subscription.getUserId(),
                NOTIFICATION_TYPE,
                title,
                body,
                "/settings/subscription",
                "SUBSCRIPTION",
                subscription.getId());
        subscription.setLastReminderAt(now);
        subscriptionRepository.save(subscription);
        return true;
    }
}
