package com.xuejiai.aaf.framework.intelligent.ai.image.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片生成请求。支持 Jackson 直接从 JSON 反序列化（无参构造 + setter）。
 *
 * <p>字段名与前端 task.params JSON 对齐，可直接通过 {@code objectMapper.readValue} 反序列化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageRequest {

    private String prompt;
    private String modelId;
    private int width = 1024;
    private int height = 1024;

    /** true 时忽略 width/height，由模型决定输出尺寸（对应前端 fixedSize="auto"）。 */
    private boolean autoSize = false;

    private String responseFormat = "url";
    private String negativePrompt;
    private int seed;
    private Boolean promptExtend;
    private int imageCount = 1;
    private String quality;
    private String format;
    private String background;
    private String moderation;
    private String sizePreset;
    private String aspectRatio;
    private String displayPrompt;
    private List<String> imageUrls;

    public ImageRequest(String prompt, String modelId) {
        this.prompt = prompt;
        this.modelId = modelId;
    }

    public ImageRequest(
            String prompt, String modelId, int width, int height, String responseFormat) {
        this.prompt = prompt;
        this.modelId = modelId;
        this.width = width;
        this.height = height;
        this.responseFormat = responseFormat;
    }

    /** 优先返回档位预设，否则返回宽高像素字符串，两者都无则返回 null。 */
    public String resolveSize() {
        if (sizePreset != null) return sizePreset;
        return (width > 0 && height > 0) ? width + "*" + height : null;
    }

    /** 图像编辑尺寸：有自定义宽高时用像素字符串，否则用档位预设。 */
    public String getEditSize() {
        return (width != 1024 || height != 1024) ? width + "*" + height : sizePreset;
    }
}
