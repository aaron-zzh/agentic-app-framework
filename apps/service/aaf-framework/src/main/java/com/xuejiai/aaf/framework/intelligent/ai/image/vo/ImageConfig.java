package com.xuejiai.aaf.framework.intelligent.ai.image.vo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 图像生成能力配置——对应 ai_model.image_config JSONB 字段，与前端 ImageConfig TS 接口保持一致。
 *
 * <p>字段存在 = 支持该参数；字段为 null = 不支持。
 *
 * @param mode 尺寸模式：ratio（按比例选）/ fixed（固定像素列表）
 * @param sizes ratio 模式：key=比例字符串，value=可选 [w,h] 列表；fixed 模式：直接是 [[w,h],...] 列表
 * @param generate 文生图配置（非 null 则支持文生图）
 * @param edit 图像编辑配置（非 null 则支持图像编辑）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageConfig(
        String mode, Object sizes, ImageModeConfig generate, ImageModeConfig edit) {

    /**
     * 单种能力的参数配置。
     *
     * @param maxImages 最多生成张数
     * @param maxInputImages 最多输入参考图张数（编辑接口）
     * @param quality 可选画质列表，如 ["standard","hd"]
     * @param format 可选格式列表，如 ["png","jpeg","webp"]
     * @param sizePresets 尺寸档位列表，如 ["1K","2K","4K"]
     * @param background 背景模式列表，如 ["auto","transparent","opaque"]
     * @param contentModeration 内容审核级别列表，如 ["auto","low"]
     * @param seed 是否支持随机种子
     * @param promptExtend 是否支持提示词智能扩写
     * @param negativePrompt 是否支持反向提示词
     * @param qualityPricing 按画质分级单价（元/张），key=quality值，如 {"standard":0.04,"hd":0.08}； null
     *     表示不分质量，统一用 model.modelPrice
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageModeConfig(
            Integer maxImages,
            Integer maxInputImages,
            List<String> quality,
            List<String> format,
            List<String> sizePresets,
            List<String> background,
            List<String> contentModeration,
            Boolean seed,
            Boolean promptExtend,
            Boolean negativePrompt,
            Map<String, BigDecimal> qualityPricing) {

        public boolean supportsSeed() {
            return Boolean.TRUE.equals(seed);
        }

        public boolean supportsPromptExtend() {
            return Boolean.TRUE.equals(promptExtend);
        }

        public boolean supportsNegativePrompt() {
            return Boolean.TRUE.equals(negativePrompt);
        }
    }

    /** 是否支持文生图 */
    public boolean supportsGenerate() {
        return generate != null;
    }

    /** 是否支持图像编辑 */
    public boolean supportsEdit() {
        return edit != null;
    }

    /** 从 ratio 模式的 sizes 中解析可用比例列表。 sizes 格式：{"1:1": [], "16:9": [[1920,1080]], ...} */
    @SuppressWarnings("unchecked")
    public List<String> ratioKeys() {
        if (!"ratio".equals(mode) || sizes == null) return List.of();
        if (sizes instanceof Map<?, ?> m) {
            return List.copyOf(((Map<String, ?>) m).keySet());
        }
        return List.of();
    }

    /** 从 ratio 模式的 sizes 中获取指定比例的可用尺寸列表。 返回 [[w,h],...] 列表，空列表表示该比例无固定尺寸限制。 */
    @SuppressWarnings("unchecked")
    public List<List<Integer>> sizesForRatio(String ratio) {
        if (!"ratio".equals(mode) || sizes == null) return List.of();
        if (sizes instanceof Map<?, ?> m) {
            Object val = ((Map<String, ?>) m).get(ratio);
            if (val instanceof List<?> list) return (List<List<Integer>>) list;
        }
        return List.of();
    }

    /** fixed 模式下的固定尺寸列表。 */
    @SuppressWarnings("unchecked")
    public List<List<Integer>> fixedSizes() {
        if (!"fixed".equals(mode) || sizes == null) return List.of();
        if (sizes instanceof List<?> list) return (List<List<Integer>>) list;
        return List.of();
    }
}
