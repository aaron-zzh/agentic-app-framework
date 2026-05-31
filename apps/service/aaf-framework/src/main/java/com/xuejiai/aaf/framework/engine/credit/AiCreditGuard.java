package com.xuejiai.aaf.framework.engine.credit;

import com.xuejiai.aaf.common.exception.InsufficientCreditsException;

/**
 * AI 能力调用积分门控接口。
 *
 * <p>积分轨 fail-closed：userId=null 或 CreditService 不可用时拒绝，不免费放行。
 *
 * <p>调用顺序：{@link #precheck} → AI 调用 → {@link #settle}
 */
public interface AiCreditGuard {

    /**
     * 调用前预检：积分余额 > 0 才放行。
     *
     * <p>余额低于预警阈值时异步发 {@link com.xuejiai.aaf.framework.intelligent.ai.chat.CreditLowEvent}，不阻塞调用。
     *
     * @param userId     用户 ID，null 时拒绝
     * @param capability 能力标识（如 "chat"/"image"/"video"）
     * @throws InsufficientCreditsException 余额不足时抛出
     * @throws IllegalStateException        userId=null 或服务不可用时抛出
     */
    void precheck(Long userId, String capability);

    /**
     * 调用成功后按实际消耗扣积分（{@link CreditService#spend}）+ 写流水。
     *
     * <p>扣减失败仅 warn，不回滚已完成的 AI 调用。
     *
     * @param userId     用户 ID
     * @param capability 能力标识
     * @param actualCost 实际消耗（token 数或次数）
     */
    void settle(Long userId, String capability, long actualCost);

    /**
     * 调用成功后按实际消耗扣积分，并指定审计关联 ID。
     *
     * @param userId     用户 ID
     * @param capability 能力标识
     * @param actualCost 实际消耗
     * @param bizId      业务流水号，如 token usageId
     */
    default void settle(Long userId, String capability, long actualCost, String bizId) {
        settle(userId, capability, actualCost);
    }
}
