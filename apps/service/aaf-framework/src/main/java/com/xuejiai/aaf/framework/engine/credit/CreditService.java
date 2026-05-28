package com.xuejiai.aaf.framework.engine.credit;

/**
 * 积分服务——管理用户积分余额和扣减。
 *
 * <p>对齐设计图"Agent 池化 × 模型选择 × 积分预算"中的 CreditService.deduct 节点。 P2 占位：当前默认实现视为无限余额，后续对接会员/充值系统。
 */
public interface CreditService {

    long getBalance(Long userId);

    boolean hasBudget(Long userId, long estimatedCost);

    void deduct(Long userId, long amount, String reason);
}
