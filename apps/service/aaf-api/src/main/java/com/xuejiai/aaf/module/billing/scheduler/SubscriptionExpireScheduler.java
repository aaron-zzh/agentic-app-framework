package com.xuejiai.aaf.module.billing.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.billing.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订阅到期处理调度器（每日 00:15）。
 *
 * <p>pending SKU 只有 FREE 才可直接切换；付费 pending 没有支付事实时统一回落 FREE_DEFAULT。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpireScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @OrgIgnore
    @Scheduled(cron = "0 15 0 * * *")
    public void expireAndSwitch() {
        var expired =
                subscriptionRepository.findByStatusAndEndAtBefore(
                        SubscriptionStatusEnum.ACTIVE.getCode(), LocalDateTime.now());

        var processed = 0;
        for (var sub : expired) {
            try {
                if (processSubscription(sub)) {
                    processed++;
                }
            } catch (Exception exception) {
                log.error(
                        "[SubscriptionExpireScheduler] 处理订阅失败: subId={}, userId={}, err={}",
                        sub.getId(),
                        sub.getUserId(),
                        exception.getMessage(),
                        exception);
            }
        }
        log.info("[SubscriptionExpireScheduler] 扫描到期订阅 {} 个，完成 {} 个", expired.size(), processed);
    }

    /** 单个到期候选交给会员服务按 guard→ACTIVE 锁序重读并处理。 */
    boolean processSubscription(Subscription sub) {
        return subscriptionService.expireAndSwitchToFree(sub.getUserId(), sub.getId());
    }
}
