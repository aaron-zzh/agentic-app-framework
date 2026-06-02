package com.xuejiai.aaf.module.billing.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.billing.EntitlementOperationEnum;
import com.xuejiai.aaf.common.enums.billing.EntitlementTypeEnum;
import com.xuejiai.aaf.common.exception.QuotaExceededException;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.engine.entitlement.EntitlementChecker;
import com.xuejiai.aaf.module.billing.domain.EntitlementDef;
import com.xuejiai.aaf.module.billing.domain.EntitlementLedger;
import com.xuejiai.aaf.module.billing.domain.EntitlementQuota;
import com.xuejiai.aaf.module.billing.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 权益消费服务——实现 EntitlementChecker 接口供 AOP 切面调用。
 *
 * <p>核心逻辑：检查 remain → 足够则扣减 → 不足尝试 refill（消耗积分）→ 仍不足抛异常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntitlementService implements EntitlementChecker {

    private final EntitlementDefRepository defRepository;
    private final EntitlementQuotaRepository quotaRepository;
    private final EntitlementLedgerRepository ledgerRepository;
    private final PlanEntitlementRepository planEntitlementRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CreditService creditService;

    /** 执行前检查额度是否足够（含 refill 可行性预判，但不真扣）。 */
    @Override
    @Transactional(readOnly = true)
    public void check(Long userId, String code, long cost) {
        var def =
                defRepository
                        .findByCode(code)
                        .orElseThrow(() -> new IllegalArgumentException("权益定义不存在: " + code));

        // BOOLEAN 类型：仅检查是否拥有该权益额度记录
        if (EntitlementTypeEnum.BOOLEAN.getCode().equals(def.getType())) {
            var quota = quotaRepository.findByUserIdAndEntId(userId, def.getId()).orElse(null);
            if (quota == null || quota.getTotal() <= 0) {
                throw new QuotaExceededException(code, 1, 0);
            }
            return;
        }

        // COUNTABLE 类型：检查 remain，不足预判 refill 可行性
        var quota =
                quotaRepository
                        .findByUserIdAndEntId(userId, def.getId())
                        .orElseThrow(() -> new QuotaExceededException(code, cost, 0));

        // 无限额度（-1）直接放行
        if (quota.getTotal() == -1) {
            return;
        }

        if (quota.getRemain() >= cost) {
            return;
        }

        // 额度不足，预判 refill 是否可行（不真扣积分）
        if (!canRefill(userId, def, quota, cost)) {
            throw new QuotaExceededException(code, cost, quota.getRemain());
        }
    }

    /** 方法成功后真扣减（含 refill 真扣积分）+ 写 ledger。 BOOLEAN 类型不扣减。 */
    @Override
    @Transactional
    public void consume(Long userId, String code, long cost) {
        var def =
                defRepository
                        .findByCode(code)
                        .orElseThrow(() -> new IllegalArgumentException("权益定义不存在: " + code));

        // BOOLEAN 类型：check 阶段已校验，consume 不扣
        if (EntitlementTypeEnum.BOOLEAN.getCode().equals(def.getType())) {
            return;
        }

        var quota =
                quotaRepository
                        .findByUserIdAndEntId(userId, def.getId())
                        .orElseThrow(() -> new QuotaExceededException(code, cost, 0));

        // 无限额度（-1）仅记录
        if (quota.getTotal() == -1) {
            writeLedger(quota.getId(), -cost, EntitlementOperationEnum.USE, null, null);
            return;
        }

        // 额度不足时执行 refill（真扣积分）
        if (quota.getRemain() < cost) {
            tryRefill(userId, def, quota, cost);
        }

        deduct(quota, cost);
    }

    /** 检查并消费权益额度（便捷方法，不走切面时直调）。 */
    @Override
    @Transactional
    public void checkAndConsume(Long userId, String code, long cost) {
        check(userId, code, cost);
        consume(userId, code, cost);
    }

    /** 实例化用户权益额度（订阅生效时调用） */
    @Transactional
    public void instantiateQuotas(Long userId, Long planId) {
        var rules = planEntitlementRepository.findByPlanId(planId);
        for (var rule : rules) {
            var existing =
                    quotaRepository.findByUserIdAndEntId(userId, rule.getEntId()).orElse(null);
            if (existing != null) {
                // 升级：更新额度
                existing.setTotal(rule.getQuota());
                existing.setRemain(rule.getQuota());
                existing.setUsed(0L);
                existing.setLastResetAt(LocalDateTime.now());
                existing.setNextResetAt(calcNextReset(rule.getResetCycle()));
                quotaRepository.save(existing);
            } else {
                // 新建额度实例
                var quota = new EntitlementQuota();
                quota.setUserId(userId);
                quota.setEntId(rule.getEntId());
                quota.setTotal(rule.getQuota());
                quota.setUsed(0L);
                quota.setRemain(rule.getQuota());
                quota.setLastResetAt(LocalDateTime.now());
                quota.setNextResetAt(calcNextReset(rule.getResetCycle()));
                quotaRepository.save(quota);
            }
        }
        log.info("用户 {} 权益额度实例化完成，套餐={}, 规则数={}", userId, planId, rules.size());
    }

    /** 周期重置：扫描到期的 quota 重置额度（供定时任务调用） */
    @Transactional
    public int resetExpiredQuotas() {
        var now = LocalDateTime.now();
        var expiredQuotas = quotaRepository.findByNextResetAtLessThanEqual(now);
        for (var quota : expiredQuotas) {
            // 查找对应的 plan_entitlement 获取 reset_cycle
            var subscription =
                    subscriptionRepository
                            .findByUserIdAndStatus(quota.getUserId(), "ACTIVE")
                            .orElse(null);
            if (subscription == null) continue;

            var rule =
                    planEntitlementRepository
                            .findByPlanIdAndEntId(subscription.getPlanId(), quota.getEntId())
                            .orElse(null);
            if (rule == null) continue;

            var oldRemain = quota.getRemain();
            quota.setTotal(rule.getQuota());
            quota.setUsed(0L);
            quota.setRemain(rule.getQuota());
            quota.setLastResetAt(now);
            quota.setNextResetAt(calcNextReset(rule.getResetCycle()));
            quotaRepository.save(quota);

            writeLedger(
                    quota.getId(),
                    rule.getQuota() - oldRemain,
                    EntitlementOperationEnum.RESET,
                    null,
                    null);
        }
        log.info("权益周期重置完成，处理 {} 条", expiredQuotas.size());
        return expiredQuotas.size();
    }

    /** 查询用户所有权益额度 */
    @Transactional(readOnly = true)
    public List<EntitlementQuota> listUserQuotas(Long userId) {
        return quotaRepository.findByUserId(userId);
    }

    // ===== 私有方法 =====

    private void deduct(EntitlementQuota quota, long cost) {
        quota.setUsed(quota.getUsed() + cost);
        quota.setRemain(quota.getRemain() - cost);
        quotaRepository.save(quota);
        writeLedger(quota.getId(), -cost, EntitlementOperationEnum.USE, null, null);
    }

    /** 预判 refill 是否可行（不真扣积分，仅检查余额） */
    private boolean canRefill(Long userId, EntitlementDef def, EntitlementQuota quota, long cost) {
        var subscription =
                subscriptionRepository.findByUserIdAndStatus(userId, "ACTIVE").orElse(null);
        if (subscription == null) return false;

        var rule =
                planEntitlementRepository
                        .findByPlanIdAndEntId(subscription.getPlanId(), def.getId())
                        .orElse(null);
        if (rule == null || rule.getRefillPrice() <= 0) return false;

        var deficit = cost - quota.getRemain();
        var refillUnits = (deficit + rule.getQuota() - 1) / rule.getQuota();
        var totalCreditCost = refillUnits * rule.getRefillPrice();

        return creditService.hasBudget(userId, totalCreditCost);
    }

    /** 尝试用积分充值额度（真扣积分） */
    private boolean tryRefill(Long userId, EntitlementDef def, EntitlementQuota quota, long cost) {
        // 查找当前订阅的 refill_price
        var subscription =
                subscriptionRepository.findByUserIdAndStatus(userId, "ACTIVE").orElse(null);
        if (subscription == null) return false;

        var rule =
                planEntitlementRepository
                        .findByPlanIdAndEntId(subscription.getPlanId(), def.getId())
                        .orElse(null);
        if (rule == null || rule.getRefillPrice() <= 0) return false;

        // 计算需要充值多少次才能满足 cost
        var deficit = cost - quota.getRemain();
        var refillUnits = (deficit + rule.getQuota() - 1) / rule.getQuota(); // 向上取整
        var totalCreditCost = refillUnits * rule.getRefillPrice();

        // 检查积分是否足够
        if (!creditService.hasBudget(userId, totalCreditCost)) {
            return false;
        }

        // 消耗积分
        creditService.spend(userId, totalCreditCost, "ENT_REFILL", def.getCode());

        // 充值额度
        var refillAmount = refillUnits * rule.getQuota();
        quota.setRemain(quota.getRemain() + refillAmount);
        quota.setTotal(quota.getTotal() + refillAmount);
        quotaRepository.save(quota);

        writeLedger(
                quota.getId(), refillAmount, EntitlementOperationEnum.REFILL, "ENT_REFILL", null);
        log.info(
                "用户 {} 权益 {} 积分充值: 消耗积分={}, 充值额度={}",
                userId,
                def.getCode(),
                totalCreditCost,
                refillAmount);
        return true;
    }

    private void writeLedger(
            Long quotaId,
            long delta,
            EntitlementOperationEnum operation,
            String bizType,
            Long bizId) {
        var ledger = new EntitlementLedger();
        ledger.setQuotaId(quotaId);
        ledger.setDelta(delta);
        ledger.setOperation(operation.getCode());
        ledger.setBizType(bizType);
        ledger.setBizId(bizId);
        ledgerRepository.save(ledger);
    }

    private LocalDateTime calcNextReset(String resetCycle) {
        var now = LocalDateTime.now();
        return switch (resetCycle) {
            case "DAILY" -> now.plusDays(1).withHour(0).withMinute(0).withSecond(0);
            case "MONTHLY" ->
                    now.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            case "YEARLY" ->
                    now.plusYears(1)
                            .withMonth(1)
                            .withDayOfMonth(1)
                            .withHour(0)
                            .withMinute(0)
                            .withSecond(0);
            default -> null; // NONE 不重置
        };
    }
}
