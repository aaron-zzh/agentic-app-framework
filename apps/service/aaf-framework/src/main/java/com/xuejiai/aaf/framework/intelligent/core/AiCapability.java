package com.xuejiai.aaf.framework.intelligent.core;

import com.xuejiai.aaf.common.enums.ai.AiQuotaTypeEnum;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

/**
 * AI 能力服务标记接口——声明能力标识和积分估算逻辑。
 *
 * <p>计费策略由 {@code AiModel.quotaType} 运行时决定（0=TOKEN，1=PER_USE）。 各能力接口可覆写 {@link #estimateCost}
 * 实现精确预估（如 OCR 图像 token 估算）。
 */
public interface AiCapability {

    /** 1元 = 100积分（积分单位为"分"） */
    double YUAN_TO_CREDIT = 100.0;

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
     * @param model 已解析的模型对象
     * @param args 被拦截方法的完整参数（args[0]=AiModel，args[1]=具体请求对象）
     * @param markupRate 积分倍率（从 sys_config 读取）
     */
    default long estimateCost(AiModel model, Object[] args, int markupRate) {
        if (model == null) return 1;
        boolean perUse =
                model.getQuotaType() != null
                        && model.getQuotaType() == AiQuotaTypeEnum.PER_USE.getCode();
        if (perUse) {
            if (model.getModelPrice() == null) return 1;
            return Math.max(
                    1,
                    Math.round(model.getModelPrice().doubleValue() * YUAN_TO_CREDIT * markupRate));
        }
        return 0;
    }
}
