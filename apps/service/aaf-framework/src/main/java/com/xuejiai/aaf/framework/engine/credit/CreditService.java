package com.xuejiai.aaf.framework.engine.credit;

import java.time.LocalDateTime;

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

    /** 赚取积分（充值场景，有效期 2 年，batch_type=TOPUP） */
    void earn(Long userId, long amount, String source, String bizId);

    /**
     * 赚取带有效期的积分批次。
     *
     * @param batchType 批次来源：SUBSCRIPTION/TOPUP/REWARD/WEEKLY/MANUAL
     * @param expireAt 过期时间，null = 永不过期
     */
    void earnBatch(
            Long userId,
            long amount,
            String batchType,
            String source,
            String bizId,
            LocalDateTime expireAt);

    /**
     * 消费积分（按批次优先扣减，允许透支），返回写入的 CreditTransaction ID。
     *
     * @param overdraftLimit 允许透支额，0 = 不允许透支
     * @param bizType 业务表标识，见 {@link com.xuejiai.aaf.common.enums.pay.CreditBizTypeEnum}
     */
    Long spend(
            Long userId,
            long amount,
            String source,
            String category,
            String bizId,
            long overdraftLimit,
            String remark,
            String bizType);

    /** 冻结积分（预扣） */
    void freeze(Long userId, long amount, String bizId);

    /** 解冻积分（释放预扣） */
    void unfreeze(Long userId, long amount, String bizId);

    /** 获取账户详情（余额/冻结/累计） */
    CreditAccount getAccount(Long userId);

    /** 分页查询流水 */
    org.springframework.data.domain.Page<CreditTransaction> getTransactions(
            Long userId, org.springframework.data.domain.Pageable pageable);

    /** 按 batch_type 汇总积分余额（供 Header 用户弹窗展示）。 返回各分组的剩余积分，只包含 remain > 0 的批次。 */
    java.util.Map<String, Long> getGroupedBalance(Long userId);
}
