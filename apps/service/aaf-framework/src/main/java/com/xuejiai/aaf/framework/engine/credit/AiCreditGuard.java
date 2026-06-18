package com.xuejiai.aaf.framework.engine.credit;

import com.xuejiai.aaf.common.enums.ai.AiQuotaTypeEnum;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;

/**
 * AI 能力调用积分门控接口。
 *
 * <p>积分轨 fail-closed：userId=null 或 CreditService 不可用时拒绝，不免费放行。
 *
 * <p>调用顺序：{@link #precheck} → AI 调用 → {@link #settle}
 */
public interface AiCreditGuard {

    /** 获取当前积分倍率（从 sys_config 读取，供 estimateCost 使用）。 默认返回 10，实现类从配置中心读取实际值。 */
    default int getMarkupRate() {
        return 10;
    }

    /**
     * 调用前预检：积分余额 > 0 才放行。
     *
     * <p>余额低于预警阈值时异步发 {@link com.xuejiai.aaf.framework.intelligent.ai.chat.CreditLowEvent}，不阻塞调用。
     *
     * @param userId 用户 ID，null 时拒绝
     * @param capability 能力标识（如 "chat"/"image"/"video"）
     * @throws InsufficientCreditsException 余额不足时抛出
     * @throws IllegalStateException userId=null 或服务不可用时抛出
     */
    void precheck(Long userId, String capability);

    /**
     * 调用前预检：余额 >= estimatedCost 才放行。
     *
     * @param userId 用户 ID
     * @param capability 能力标识
     * @param estimatedCost 预估消耗积分数
     * @throws InsufficientCreditsException 余额不足时抛出
     */
    default void precheck(Long userId, String capability, long estimatedCost) {
        precheck(userId, capability); // 默认降级到 > 0 检查
    }

    /**
     * 调用成功后按实际消耗扣积分（{@link CreditService#spend}）+ 写流水。
     *
     * <p>扣减失败仅 warn，不回滚已完成的 AI 调用。
     *
     * @param userId 用户 ID
     * @param capability 能力标识
     * @param actualCost 实际消耗（token 数或次数）
     */
    void settle(Long userId, String capability, long actualCost);

    /**
     * 调用成功后按实际消耗扣积分，并指定审计关联 ID。
     *
     * @param userId 用户 ID
     * @param capability 能力标识
     * @param actualCost 实际消耗
     * @param bizId 业务流水号，如 token usageId
     */
    default void settle(Long userId, String capability, long actualCost, String bizId) {
        settle(userId, capability, actualCost);
    }

    /**
     * 按模型 input/output token 分离计费。
     *
     * @param userId 用户 ID
     * @param modelId 模型数据库 ID，用于查价格
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param bizId 业务流水号
     */
    default void settleByModel(
            Long userId, Long modelId, long inputTokens, long outputTokens, String bizId) {
        settleByModel(userId, modelId, inputTokens, outputTokens, bizId, null);
    }

    /** 同上，额外传入 remark 写入积分流水备注。 */
    default void settleByModel(
            Long userId,
            Long modelId,
            long inputTokens,
            long outputTokens,
            String bizId,
            String remark) {
        // 默认降级：加总后走通用 settle
        settle(userId, "chat", inputTokens + outputTokens, bizId);
    }

    /**
     * 按次计费（图像/视频生成等固定单价场景）。
     *
     * @param userId 用户 ID
     * @param modelId 模型数据库 ID，用于查 model_price
     * @param bizId 业务流水号
     */
    default void settlePerUse(Long userId, Long modelId, String bizId) {
        settlePerUse(userId, modelId, bizId, null);
    }

    /** 同上，额外传入 remark 写入积分流水备注。 */
    default void settlePerUse(Long userId, Long modelId, String bizId, String remark) {
        settle(userId, "image", 1, bizId);
    }

    /**
     * 按模型 quotaType 自动决策结算方式（统一入口，同步/异步回调均可调用）。
     *
     * <p>quotaType == 1（按次）→ {@link #settlePerUse}；其余（按 token）→ {@link #settleByModel}。
     *
     * @param userId 用户 ID
     * @param model 已解析的模型对象（含 quotaType / modelPrice / id）
     * @param inputTokens 输入 token 数（按次时忽略）
     * @param outputTokens 输出 token 数（按次时忽略）
     * @param bizId 业务流水号
     */
    default void settleByUsage(
            Long userId,
            com.xuejiai.aaf.framework.intelligent.core.model.AiModel model,
            long inputTokens,
            long outputTokens,
            String bizId) {
        settleByUsage(userId, model, inputTokens, outputTokens, bizId, null);
    }

    /** 同上，额外传入 remark 写入积分流水备注。 */
    default void settleByUsage(
            Long userId,
            com.xuejiai.aaf.framework.intelligent.core.model.AiModel model,
            long inputTokens,
            long outputTokens,
            String bizId,
            String remark) {
        Long modelId = model != null ? model.getId() : null;
        boolean perUse =
                model != null
                        && model.getQuotaType() != null
                        && model.getQuotaType() == AiQuotaTypeEnum.PER_USE.getCode();
        if (perUse) {
            settlePerUse(userId, modelId, bizId, remark);
        } else {
            settleByModel(userId, modelId, inputTokens, outputTokens, bizId, remark);
        }
    }
}
