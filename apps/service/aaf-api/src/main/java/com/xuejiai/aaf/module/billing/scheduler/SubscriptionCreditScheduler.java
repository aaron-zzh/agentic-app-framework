package com.xuejiai.aaf.module.billing.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 月度积分发放定时任务。
 *
 * <p>每日凌晨 00:05 扫描所有有效订阅，对距上次发放 ≥ 30 天的订阅发放下一批月度积分。 可通过系统配置 {@code member.monthly_grant_enabled}
 * 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionCreditScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final CreditService creditService;
    private final SystemConfigService systemConfigService;

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void issueMonthlyCredits() {
        if (!systemConfigService.getBoolean(SysConfigKeys.Member.MONTHLY_GRANT_ENABLED, true)) {
            log.info("月度积分发放已关闭（{}=false）", SysConfigKeys.Member.MONTHLY_GRANT_ENABLED);
            return;
        }

        var now = LocalDateTime.now();
        var threshold = now.minusDays(30);

        var subscriptions =
                subscriptionRepository
                        .findByStatusAndLastCreditIssuedAtBeforeOrLastCreditIssuedAtIsNull(
                                SubscriptionStatusEnum.ACTIVE.getCode(), threshold);

        int issued = 0;
        for (var sub : subscriptions) {
            var plan = planRepository.findById(sub.getPlanId()).orElse(null);
            if (plan == null || plan.getMonthlyCredits() <= 0) continue;

            creditService.earnBatch(
                    sub.getUserId(),
                    plan.getMonthlyCredits(),
                    "SUBSCRIPTION",
                    "SUBSCRIPTION_MONTHLY",
                    String.valueOf(sub.getId()),
                    now.plusDays(30));

            sub.setLastCreditIssuedAt(now);
            subscriptionRepository.save(sub);
            issued++;
        }
        log.info("月度积分发放完成，共发放 {} 个订阅", issued);
    }
}
