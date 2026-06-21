package com.xuejiai.aaf.framework.engine.credit;

/**
 * 订阅升级三笔流水结算结果。
 *
 * <p>结构对应 membership-completion.md F2 章节的"积分立即结算"：EXPIRE / EARN / SPEND 三笔流水。
 *
 * @param expireTxId 旧 SUBSCRIPTION 批次清零流水 ID（旧批次 remain=0 时为 null）
 * @param earnTxId 新月度积分流水 ID
 * @param spendTxId 升级继承已用流水 ID（oldUsed=0 时为 null）
 * @param oldUsed 实际继承的已用积分
 */
public record UpgradeSettlement(Long expireTxId, Long earnTxId, Long spendTxId, long oldUsed) {}
