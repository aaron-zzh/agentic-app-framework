package com.xuejiai.aaf.framework.engine.settlement;

import java.math.BigDecimal;

/**
 * 结算引擎——Token 消耗的费用计算与账单生成。
 *
 * <p>职责：按模型单价计算费用、生成账单、对账。
 * v0.2+ 实现。
 */
public interface SettlementEngine {

    /** 计算单次调用费用。 */
    BigDecimal calculate(String modelId, long promptTokens, long completionTokens);

    /** 生成用户月度账单。 */
    BillSummary generateBill(Long userId, int year, int month);

    /** 账单摘要 */
    record BillSummary(Long userId, int year, int month, BigDecimal totalCost, long totalTokens) {}
}
