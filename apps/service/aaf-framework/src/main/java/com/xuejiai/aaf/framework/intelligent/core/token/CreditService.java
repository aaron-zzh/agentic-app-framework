package com.xuejiai.aaf.framework.intelligent.core.token;

/**
 * 积分服务——管理用户积分余额和扣减。
 *
 * <p>对齐设计图"Agent 池化 × 模型选择 × 积分预算"中的 CreditService.deduct 节点。
 * P2 占位：当前默认实现视为无限余额，后续对接会员/充值系统。
 */
public interface CreditService {

    /**
     * 查询用户当前积分余额。
     *
     * @param userId 用户 ID
     * @return 积分余额（-1 表示无限制）
     */
    long getBalance(Long userId);

    /**
     * 预算检查：余额是否足够执行一次请求。
     *
     * @param userId 用户 ID
     * @param estimatedCost 预估消耗积分
     * @return true=余额充足
     */
    boolean hasBudget(Long userId, long estimatedCost);

    /**
     * 扣减积分（执行完成后调用）。
     *
     * @param userId 用户 ID
     * @param amount 实际消耗积分
     * @param reason 扣减原因（如 modelId + tokenCount）
     */
    void deduct(Long userId, long amount, String reason);
}
