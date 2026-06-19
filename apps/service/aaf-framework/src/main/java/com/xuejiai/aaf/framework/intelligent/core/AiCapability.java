package com.xuejiai.aaf.framework.intelligent.core;

import com.xuejiai.aaf.common.enums.ai.AiQuotaTypeEnum;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

/**
 * AI 能力服务标记接口——声明能力标识和积分估算逻辑。
 *
 * <p>计费策略由 {@code AiModel.quotaType} 运行时决定（0=TOKEN，1=PER_USE）。 各能力接口可覆写 {@link #estimateCost}
 * 实现精确预估（如 OCR 图像 token 估算）。
 */
public interface AiCapability {

    /** 能力标识，与 CapabilityRoutingContext 常量及积分流水 category 保持一致。 */
    default String capability() {
        return "AI";
    }

    /** 积分流水业务名称，写入 remark 字段供用户查看（如 "OCR 识别"）。默认空，子类按需覆写。 */
    default String bizName() {
        return "";
    }

    /**
     * 估算本次调用的积分预检费用。
     *
     * <p>默认按 quotaType 分支：
     *
     * <ul>
     *   <li>TOKEN(0)：token 计费，无法预估，返回 0（结算时按实际 token 扣减）
     *   <li>PER_USE(1)：按次，返回 modelPrice × YUAN_TO_CREDIT × markupRate
     *   <li>PER_SEC(2)：按秒，默认兜底 1 秒，子类覆写从 req 读取预估时长
     *   <li>PER_UNIT(3)：按单元，默认兜底 1 单元，子类覆写从 req 读取单元数/单价
     * </ul>
     *
     * @param model 已解析的模型对象
     * @param req 本次调用的请求对象（子类按需强转使用）
     * @param markupRate 积分倍率（从 sys_config 读取）
     */
    default long estimateCost(AiModel model, Object req, int markupRate) {
        if (model == null || model.getQuotaType() == null) return 1;
        return switch (AiQuotaTypeEnum.of(model.getQuotaType())) {
            case PER_USE, PER_SEC, PER_UNIT ->
                    AiCreditGuard.calcPerUseCost(model.getModelPrice(), markupRate);
            default -> 0;
        };
    }
}
