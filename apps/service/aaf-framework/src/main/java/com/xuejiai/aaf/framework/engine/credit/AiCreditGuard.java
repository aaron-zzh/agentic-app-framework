package com.xuejiai.aaf.framework.engine.credit;

import java.math.BigDecimal;

import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

/**
 * AI 能力调用积分门控接口。
 *
 * <p>积分轨 fail-closed：userId=null 或 CreditService 不可用时拒绝，不免费放行。
 *
 * <p>调用顺序：{@link #precheck} → AI 调用 → {@link #settleByUsage(Long, AiModel, AiUsage, String,
 * String)}
 */
public interface AiCreditGuard {

    /** 1元 = 100积分（积分单位为"分"）。所有计费计算统一引用此常量。 */
    double YUAN_TO_CREDIT = 100.0;

    /** 传入 precheck 的预估值：表示无法估算，降级为余额 > 0 保守检查。 */
    long INESTIMABLE_COST = 0L;

    /** 计算按次/按单位积分费用（预估与结算共用，避免重复逻辑）。 */
    static long calcPerUseCost(BigDecimal modelPrice, int markupRate) {
        if (modelPrice == null) return 1;
        return Math.max(1, Math.round(modelPrice.doubleValue() * YUAN_TO_CREDIT * markupRate));
    }

    /** 获取当前积分倍率（从 sys_config 读取）。默认返回 5，实现类从配置中心读取实际值。 */
    default int getMarkupRate() {
        return 5;
    }

    /**
     * 检查用户是否有足够余额（estimatedCost=0 时只检查余额 > 0）。
     *
     * @param userId 用户 ID
     * @param estimatedCost 预估消耗积分数
     * @return true 表示余额充足
     */
    boolean hasBudget(Long userId, long estimatedCost);

    /**
     * 调用前预检：余额 >= estimatedCost 才放行（estimatedCost=0 时只检查余额 > 0）。
     *
     * @param userId 用户 ID，null 时拒绝
     * @param capability 能力标识（如 "chat"/"image"/"video"）
     * @param estimatedCost 预估消耗积分数，0 时降级为余额 > 0 检查
     * @throws InsufficientCreditsException 余额不足时抛出
     */
    void precheck(Long userId, String capability, long estimatedCost);

    /**
     * 按固定积分数结算（工具调用等已预算好费用的场景）。
     *
     * <p>适用于 {@code ToolCatalogEntry.costExpression} 非 null 的第三方 API 类工具，
     * 费用由工具目录静态配置，不经过模型定价体系。当前所有内置工具 costExpression=null， 此方法暂不触发，作为扩展点预留。
     *
     * @param userId 用户 ID
     * @param creditCost 已计算好的积分数
     * @param capability 能力标识（工具名称）
     */
    default void settleFixed(Long userId, long creditCost, String capability) {
        // 占位：实现类覆写以写入 AiUsageRecord
    }

    /**
     * 按 {@link AiUsage} 结算（统一入口）。
     *
     * <p>根据 {@code model.quotaType} 自动路由结算方式，并写入 {@link AiUsageRecord}：
     *
     * <ul>
     *   <li>TOKEN → 按 inputTokens/outputTokens 分别计价
     *   <li>PER_USE → 按次固定单价
     *   <li>PER_SEC → 按 usage.duration() 实际秒数
     *   <li>PER_UNIT→ 按单元（分辨率等）
     * </ul>
     *
     * @param userId 用户 ID
     * @param model 已解析的模型对象（null 时走 TOKEN fallback）
     * @param usage 本次调用用量（结果类实现 AiUsage 接口提供）
     * @param capability 能力标识，写入积分流水 category
     * @param remark 积分流水备注
     */
    void settleByUsage(Long userId, AiModel model, AiUsage usage, String capability, String remark);
}
