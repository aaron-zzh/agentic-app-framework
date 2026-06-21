package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一视频生成请求。
 *
 * <p>路由完全由 {@code imageMode} 决定，调用方必须显式传入意图，不依赖字段是否为空推断。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoRequest {

    private String prompt;

    /** 首帧图片 URL，传入则走 i2v。 */
    private String imageUrl;

    /** 参考图片 URL 列表（1~9张），传入则走 r2v。prompt 中用 [Image 1] 等指代。 */
    private List<String> referenceImageUrls;

    private String model;
    private String resolution;
    private String ratio;
    private Integer duration;
    private Integer seed;

    /** 单张图片时的业务意图：T2V（默认，文生视频）、FIRST_FRAME（首帧，走 i2v）、REFERENCE（参考图，走 r2v）。 */
    private ImageMode imageMode;

    public enum ImageMode {
        T2V,
        FIRST_FRAME,
        REFERENCE
    }
}
