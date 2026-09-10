package com.xuejiai.aaf.module.billing.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.billing.repository.MembershipCheckoutGuardRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 每日扫描到期的月度积分批次，在统一会员锁序内完成发放和时间戳更新。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionCreditScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final MembershipCheckoutGuardRepository checkoutGuardRepository;
    private final CreditService creditService;
    private final SystemConfigService systemConfigService;

    @OrgIgnore
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void issueMonthlyCredits() {
        if (!systemConfigService.getBoolean(SysConfigKeys.Member.MONTHLY_GRANT_ENABLED, true)) {
            log.info("月度积分发放已关闭（{}=false）", SysConfigKeys.Member.MONTHLY_GRANT_ENABLED);
            return;
        }

        var now = LocalDateTime.now();
        var threshold = now.minusDays(30);
        var candidates =
                subscriptionRepository
                        .findByStatusAndLastCreditIssuedAtBeforeOrLastCreditIssuedAtIsNull(
                                SubscriptionStatusEnum.ACTIVE.getCode(), threshold);

        var issued = 0;
        for (var candidate : candidates) {
            checkoutGuardRepository.ensureGuard(candidate.getUserId());
            checkoutGuardRepository.lockGuard(candidate.getUserId());
            var current =
                    subscriptionRepository
                            .findByUserIdAndStatusForUpdate(
                                    candidate.getUserId(), SubscriptionStatusEnum.ACTIVE.getCode())
                            .orElse(null);
            if (current == null
                    || !current.getId().equals(candidate.getId())
                    || (current.getLastCreditIssuedAt() != null
                            && current.getLastCreditIssuedAt().isAfter(threshold))) {
                continue;
            }

            var plan = planRepository.findById(current.getPlanId()).orElse(null);
            if (plan == null || plan.getMonthlyCredits() == null || plan.getMonthlyCredits() <= 0) {
                continue;
            }

            creditService.earnBatch(
                    current.getUserId(),
                    plan.getMonthlyCredits(),
                    "SUBSCRIPTION",
                    "SUBSCRIPTION_MONTHLY",
                    String.valueOf(current.getId()),
                    now.plusDays(30));
            current.setLastCreditIssuedAt(now);
            subscriptionRepository.save(current);
            issued++;
        }
        log.info("月度积分发放完成，共发放 {} 个订阅", issued);
    }
}
