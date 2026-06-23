package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 文生视频请求。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TextToVideoRequest extends VideoBaseRequest {

    /** 由 CapabilityRouter 解析后的模型，实现类从此取 modelName / apiKey 等。 */
    private AiModel resolvedModel;

    /** 画面比例，如 "16:9"。 */
    private String ratio;

    /** 是否开启提示词扩写（wan2 系列支持）。 */
    private Boolean promptExtend;

    public TextToVideoRequest(
            String prompt,
            AiModel resolvedModel,
            String resolution,
            String ratio,
            Integer duration,
            Integer seed,
            Boolean promptExtend) {
        super(
                prompt,
                resolvedModel != null ? resolvedModel.getModelId() : null,
                resolution,
                duration,
                seed);
        this.resolvedModel = resolvedModel;
        this.ratio = ratio;
        this.promptExtend = promptExtend;
    }

    public TextToVideoRequest(
            String prompt,
            String modelId,
            String resolution,
            String ratio,
            Integer duration,
            Integer seed,
            Boolean promptExtend) {
        super(prompt, modelId, resolution, duration, seed);
        this.resolvedModel = null;
        this.ratio = ratio;
        this.promptExtend = promptExtend;
    }
}
