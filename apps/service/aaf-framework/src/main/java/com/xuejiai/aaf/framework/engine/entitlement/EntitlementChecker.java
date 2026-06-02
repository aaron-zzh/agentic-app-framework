package com.xuejiai.aaf.framework.engine.entitlement;

/**
 * 权益检查器接口——框架层定义，业务层（billing 模块）实现。
 *
 * <p>AOP 切面通过此接口执行权益检查和扣减，不直接依赖业务模块。
 *
 * <p>调用顺序：check（执行前）→ 方法执行 → consume（成功后）。
 */
public interface EntitlementChecker {

    /**
     * 执行前检查额度是否足够（含 refill 可行性预判，但不真扣）。
     *
     * @param userId 用户 ID
     * @param code 权益编码
     * @param cost 消耗额度（BOOLEAN 类型传 0）
     * @throws com.xuejiai.aaf.common.exception.QuotaExceededException 额度不足时抛出
     */
    void check(Long userId, String code, long cost);

    /**
     * 方法成功后真扣减（含 refill 真扣积分）+ 写 ledger。
     *
     * <p>BOOLEAN 类型不扣减，仅 check 阶段校验拥有。
     *
     * @param userId 用户 ID
     * @param code 权益编码
     * @param cost 消耗额度
     */
    void consume(Long userId, String code, long cost);

    /**
     * 检查并消费权益额度（便捷方法，不走切面时直调）。
     *
     * @param userId 用户 ID
     * @param code 权益编码
     * @param cost 消耗额度（BOOLEAN 类型传 0）
     * @throws com.xuejiai.aaf.common.exception.QuotaExceededException 额度不足时抛出
     */
    void checkAndConsume(Long userId, String code, long cost);
}
