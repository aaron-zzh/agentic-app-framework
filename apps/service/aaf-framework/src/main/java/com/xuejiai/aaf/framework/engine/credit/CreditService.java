package com.xuejiai.aaf.framework.engine.credit;

/**
 * 积分服务——管理用户积分余额、赚取、消费、冻结。
 *
 * <p>积分单位为"分"（最小不可分割单位），所有金额均为 long 类型。
 */
public interface CreditService {

    /** 获取可用余额（不含冻结） */
    long getBalance(Long userId);

    /** 预算检查：可用余额是否 >= estimatedCost */
    boolean hasBudget(Long userId, long estimatedCost);

    /** 赚取积分 */
    void earn(Long userId, long amount, String source, String bizId);

    /** 消费积分 */
    void spend(Long userId, long amount, String source, String bizId);

    /** 冻结积分（预扣） */
    void freeze(Long userId, long amount, String bizId);

    /** 解冻积分（释放预扣） */
    void unfreeze(Long userId, long amount, String bizId);
}
