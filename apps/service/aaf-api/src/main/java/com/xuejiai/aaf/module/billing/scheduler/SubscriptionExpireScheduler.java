package com.xuejiai.aaf.module.billing.scheduler;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.billing.service.SubscriptionAutoRenewService;
import com.xuejiai.aaf.module.billing.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 订阅到期处理调度器（每日 00:15）。
 *
 * <p>处理逻辑：
 *
 * <ul>
 *   <li>有 pending_plan_id 且 pending = FREE → 旧订阅 EXPIRED + 直接激活 FREE
 *   <li>有 pending_plan_id 且 pending = 付费档 →（本期）走冻结分支（未来接入 {@link SubscriptionAutoRenewService}
 *       后改为代扣 → 激活 / 失败 fallback freeze）
 *   <li>无 pending_plan_id → 冻结（旧订阅 EXPIRED + 自动激活 FREE 兜底）
 * </ul>
 *
 * <p>"冻结"语义不引入独立 FROZEN 状态，复用 EXPIRED + 自动 FREE 兜底；已发的 SUBSCRIPTION 批次积分按原 30 天有效期保留，不主动作废。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpireScheduler {

    /** FREE 套餐编码——冻结时用于自动激活兜底。 */
    private static final String FREE_PLAN_CODE = "FREE";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionService subscriptionService;

    /** 自动续费扩展点（本期未实现，注入为 null，未来接入后改为强依赖）。 */
    @Autowired(required = false)
    private SubscriptionAutoRenewService autoRenewService;

    @Scheduled(cron = "0 15 0 * * *")
    @Transactional
    public void expireAndSwitch() {
        var now = LocalDateTime.now();
        var expired =
                subscriptionRepository.findByStatusAndEndAtBefore(
                        SubscriptionStatusEnum.ACTIVE.getCode(), now);

        for (var sub : expired) {
            try {
                processSubscription(sub);
            } catch (Exception e) {
                log.error(
                        "[SubscriptionExpireScheduler] 处理订阅失败: subId={}, userId={}, err={}",
                        sub.getId(),
                        sub.getUserId(),
                        e.getMessage(),
                        e);
            }
        }
        log.info("[SubscriptionExpireScheduler] 处理到期订阅 {} 个", expired.size());
    }

    /** 处理单个到期订阅。可见性放宽到 package 便于单元测试。 */
    void processSubscription(Subscription sub) {
        if (sub.getPendingPlanId() != null) {
            var pendingPlan = planRepository.findById(sub.getPendingPlanId()).orElse(null);
            if (pendingPlan != null && pendingPlan.getPrice() == 0) {
                // → FREE：直接激活，不付费
                expire(sub);
                subscriptionService.activateSubscription(
                        sub.getUserId(), sub.getPendingPlanId(), null, false);
                log.info(
                        "订阅到期切换至 pending（FREE）: userId={}, oldPlanId={}, newPlanId={}",
                        sub.getUserId(),
                        sub.getPlanId(),
                        sub.getPendingPlanId());
                return;
            }
            // → 付费档：本期不做自动代扣，直接进入冻结分支
            // 未来接入代扣后，此处调用 autoRenewService.tryAutoCharge(sub)：
            //   - 成功：激活 pendingPlan / 同档续费
            //   - 失败 / 未签约：走 freeze 路径
            if (autoRenewService != null && autoRenewService.tryAutoCharge(sub)) {
                log.info("订阅自动续费成功: userId={}, subId={}", sub.getUserId(), sub.getId());
                return;
            }
            freeze(sub);
            return;
        }
        // 无 pending：未续费即冻结
        freeze(sub);
    }

    /** 标记订阅为 EXPIRED（不重置状态、不动 cancelled_at）。 */
    private void expire(Subscription sub) {
        sub.setStatus(SubscriptionStatusEnum.EXPIRED.getCode());
        subscriptionRepository.save(sub);
    }

    /**
     * 冻结：旧订阅 EXPIRED + 自动激活 FREE 兜底。
     *
     * <p>已发的 SUBSCRIPTION 批次积分按原 30 天有效期自然过期，不主动作废。
     */
    private void freeze(Subscription sub) {
        expire(sub);
        SubscriptionPlan freePlan = planRepository.findByCode(FREE_PLAN_CODE).orElse(null);
        if (freePlan == null) {
            log.warn("[SubscriptionExpireScheduler] FREE 套餐不存在，跳过自动激活: userId={}", sub.getUserId());
            return;
        }
        subscriptionService.activateSubscription(sub.getUserId(), freePlan.getId(), null, false);
        log.info(
                "订阅冻结至 FREE: userId={}, oldPlanId={}, newSubId(FREE)={}",
                sub.getUserId(),
                sub.getPlanId(),
                freePlan.getId());
    }
}
