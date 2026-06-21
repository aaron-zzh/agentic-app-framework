package com.xuejiai.aaf.framework.intelligent.ai.image;

import com.xuejiai.aaf.common.enums.ai.AiQuotaTypeEnum;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.vo.ImageResult;
import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

/** 文生图服务接口，支持按模型动态切换（DALL-E / 通义万象 wanx 等）。 */
public interface ImageGenerationService extends AiCapability {

    @Override
    default String capability() {
        return CapabilityRoutingContext.CAP_IMAGE_GEN;
    }

    @Override
    default String bizName() {
        return "图像生成";
    }

    @Override
    default String bizRemark(AiUsage usage) {
        int count = usage != null ? usage.count() : 1;
        return count > 1 ? "图像生成 ×" + count + " 张" : "图像生成";
    }

    /**
     * 按张估算积分预检费用。
     *
     * <p>TOKEN 计费（Gemini）→ 返回 0，结算时按实际 token 扣减。 其余模型按张计费：
     *
     * <ol>
     *   <li>优先从 image_config.generate.qualityPricing 读 quality 对应单价（元/张）
     *   <li>无分级配置则用 model.modelPrice（元/张）
     *   <li>count 取 request.imageCount，兜底 1
     * </ol>
     */
    @Override
    default long estimateCost(AiModel model, Object req, int markupRate) {
        if (model == null) return 1;
        // TOKEN 计费（Gemini）→ 预检不扣积分，结算时按实际 token
        if (AiQuotaTypeEnum.of(model.getQuotaType()) == AiQuotaTypeEnum.TOKEN) return 0;

        int count = 1;
        String quality = null;
        if (req instanceof ImageRequest ir) {
            count = Math.max(1, ir.getImageCount());
            quality = ir.getQuality();
        }

        // 尝试从 image_config 读 quality 分级单价
        var ic = model.getImageConfigParsed();
        if (ic != null && ic.generate() != null && ic.generate().qualityPricing() != null) {
            var qp = ic.generate().qualityPricing();
            // 有 quality 且命中分级 → 用分级单价；否则取第一档或回退 modelPrice
            double pricePerImage =
                    (quality != null && qp.containsKey(quality))
                            ? qp.get(quality).doubleValue()
                            : (qp.isEmpty()
                                    ? fallbackPrice(model)
                                    : qp.values().iterator().next().doubleValue());
            return Math.max(
                    1,
                    Math.round(pricePerImage * count * AiCreditGuard.YUAN_TO_CREDIT * markupRate));
        }

        // 兜底：modelPrice × count × markupRate
        return Math.max(
                1,
                Math.round(
                        fallbackPrice(model) * count * AiCreditGuard.YUAN_TO_CREDIT * markupRate));
    }

    private double fallbackPrice(AiModel model) {
        return model.getModelPrice() != null ? model.getModelPrice().doubleValue() : 0.04;
    }

    /**
     * 文生图。
     *
     * @param model 使用的 AI 模型
     * @param request 生成请求
     * @return 生成结果（URL 或 Base64）
     */
    ImageResult generate(AiModel model, ImageRequest request);

    /**
     * 图生图（参考图 + 风格 Prompt + 强度）。
     *
     * @param model 使用的 AI 模型
     * @param request 编辑请求（sourceUrl 必填，maskUrl 为 null）
     * @return 生成结果
     */
    ImageResult imageToImage(AiModel model, ImageEditRequest request);

    /**
     * 局部编辑（原图 + 蒙版区域 + 编辑 Prompt）。
     *
     * @param model 使用的 AI 模型
     * @param request 编辑请求（sourceUrl + maskUrl + prompt）
     * @return 生成结果
     */
    ImageResult editImage(AiModel model, ImageEditRequest request);

    /**
     * 图片生成请求。
     *
     * @param prompt 提示词
     * @param modelId ai_model 表中的 modelId
     * @param width 宽度（像素）
     * @param height 高度（像素）
     * @param responseFormat 返回格式：url / b64_json
     * @param negativePrompt 反向提示词（模型支持时生效）
     * @param seed 随机种子，0 表示不指定（模型支持时生效）
     * @param promptExtend 是否开启提示词智能改写（模型支持时生效）
     * @param count 生成张数，默认 1（模型支持时生效）
     */
}
