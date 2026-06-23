package com.xuejiai.aaf.framework.intelligent.ai.model3d;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

/**
 * 3D 模型生成服务接口（异步任务模式）。
 *
 * @author AaronZZH & Kiro
 */
public interface Model3dGenerationService extends AiCapability {

    @Override
    default String capability() {
        return CapabilityRoutingContext.CAP_MODEL_3D;
    }

    /**
     * 从模型 params_config 的 pricing 矩阵按 source + textureQuality 查价格。
     *
     * <p>params_config 格式：
     *
     * <pre>{"pricing":[{"source":"text","texture":"none","price":2.1}, ...]}</pre>
     *
     * <p>source 取自 req 类型：TextTo3dRequest→text，ImageTo3dRequest→image，MultiImageTo3dRequest→multi。
     */
    @Override
    default long estimateCost(AiModel model, Object req, int markupRate) {
        String source =
                switch (req) {
                    case TextTo3dRequest ignored -> "text";
                    case ImageTo3dRequest ignored -> "image";
                    case MultiImageTo3dRequest ignored -> "multi";
                    default -> "text";
                };
        String texture =
                switch (req) {
                    case TextTo3dRequest r ->
                            r.textureQuality() != null ? r.textureQuality() : "none";
                    case ImageTo3dRequest r ->
                            r.textureQuality() != null ? r.textureQuality() : "none";
                    case MultiImageTo3dRequest r ->
                            r.textureQuality() != null ? r.textureQuality() : "none";
                    default -> "none";
                };

        double price = lookupPrice(model, source, texture);
        return Math.max(1, Math.round(price * AiCreditGuard.YUAN_TO_CREDIT * markupRate));
    }

    /** 从 params_config 查价格，找不到时兜底用 model_price。 */
    static double lookupPrice(AiModel model, String source, String texture) {
        if (model == null) return 2.1;
        var pc = model.getParamsConfigParsed(Model3dParamsConfig.class);
        if (pc != null && pc.pricing() != null) {
            return pc.pricing().stream()
                    .filter(e -> source.equals(e.source()) && texture.equals(e.texture()))
                    .mapToDouble(e -> e.price().doubleValue())
                    .findFirst()
                    .orElseGet(
                            () ->
                                    model.getModelPrice() != null
                                            ? model.getModelPrice().doubleValue()
                                            : 2.1);
        }
        return model.getModelPrice() != null ? model.getModelPrice().doubleValue() : 2.1;
    }

    /** 3D 模型 params_config 结构。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Model3dParamsConfig(List<Model3dPricingEntry> pricing) {}

    /** 定价矩阵条目。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Model3dPricingEntry(String source, String texture, BigDecimal price) {}

    /** 文生 3D，返回 taskId。 */
    String submitTextTo3d(TextTo3dRequest request);

    /** 单图生 3D，返回 taskId。 */
    String submitImageTo3d(ImageTo3dRequest request);

    /** 多图生 3D（前/左/后/右四视角），返回 taskId。 */
    String submitMultiImageTo3d(MultiImageTo3dRequest request);

    /** 查询任务结果。 */
    Model3dTaskResult query(String taskId);

    /** 文生 3D 请求。 */
    record TextTo3dRequest(
            String prompt,
            /** 贴图质量：standard / detailed */
            String textureQuality,
            /** 是否生成 PBR 材质，默认 true */
            Boolean pbr) {}

    /** 单图生 3D 请求。 */
    record ImageTo3dRequest(String imageUrl, String textureQuality, Boolean pbr) {}

    /** 多图生 3D 请求（四视角：前/左/后/右）。 */
    record MultiImageTo3dRequest(
            /** 四视角图片 URL 列表（前/左/后/右），不需要的视角传 null */
            List<ImageInput> images, String textureQuality, Boolean pbr) {}

    /** 多图输入项。 */
    record ImageInput(String type, String fileToken) {}

    /** 任务查询结果。 */
    record Model3dTaskResult(
            String taskId,
            TaskStatus status,
            /** PBR 材质模型 URL（GLB） */
            String modelUrl,
            /** 无贴图基础模型 URL */
            String baseModelUrl,
            /** 渲染预览图 URL */
            String thumbnailUrl,
            String prompt,
            /** 来源：text / image / multi */
            String source,
            /** 贴图质量：none / standard / detailed */
            String textureQuality)
            implements AiUsage {

        /** 兼容旧调用（无 source/textureQuality）。 */
        public Model3dTaskResult(
                String taskId,
                TaskStatus status,
                String modelUrl,
                String baseModelUrl,
                String thumbnailUrl,
                String prompt) {
            this(taskId, status, modelUrl, baseModelUrl, thumbnailUrl, prompt, null, null);
        }

        @Override
        public Map<String, Object> standardUsage() {
            var map = new java.util.HashMap<String, Object>();
            map.put("count", 1);
            if (source != null) map.put("source", source);
            if (textureQuality != null) map.put("textureQuality", textureQuality);
            return map;
        }

        public enum TaskStatus {
            PENDING,
            RUNNING,
            SUCCEEDED,
            FAILED
        }
    }
}
