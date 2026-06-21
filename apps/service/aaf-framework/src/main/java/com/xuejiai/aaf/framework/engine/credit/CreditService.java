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

    /**
     * 退还此前已扣减的积分（写反向 EARN 流水，不还原原批次 remain）。
     *
     * <p>语义：
     *
     * <ul>
     *   <li>仅对 type=SPEND 的流水有效，其他类型返回 null
     *   <li>幂等：若该 creditTxId 已被退还过，再次调用返回 null
     *   <li>退还的积分有效期取继承策略——同账户最近到期批次的 expire_at；无活跃批次则 30 天
     *   <li>原扣款流水的 remain 不还原（作为已发生事实保留），保证审计可溯
     * </ul>
     *
     * @param creditTxId 原扣款流水 ID
     * @param reason 退还原因（写入 remark）
     * @return 退还流水 ID；找不到原流水或非 SPEND 类型或已退过返回 null
     */
    Long refund(Long creditTxId, String reason);

    /**
     * 订阅升级三笔流水结算：清零旧 SUBSCRIPTION 批次（EXPIRE）→ 发新月度积分（EARN）→ 继承已用 SPEND。
     *
     * <p>设计见 docs/design/apps/service/membership-completion.md F2 章节。
     *
     * <p>典型场景："旧月度 200 已消耗 100，升级到月度 400" → 升级后 balance=300（总额 400 - 已消耗 100）。
     *
     * @param userId 用户 ID
     * @param newAmount 新套餐月度积分总额
     * @param newSubId 新订阅 ID（用于流水 biz_id）
     * @param newExpireAt 新批次过期时间（典型为 now + 30 天）
     * @return 三笔流水的结算结果
     */
    UpgradeSettlement settleSubscriptionUpgrade(
            Long userId, long newAmount, Long newSubId, LocalDateTime newExpireAt);

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
