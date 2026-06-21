package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 图生视频请求（首帧模式）。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ImageToVideoRequest extends VideoBaseRequest {

    /** 首帧图片 URL。 */
    private String firstFrameUrl;

    public ImageToVideoRequest(
            String prompt,
            String firstFrameUrl,
            String model,
            String resolution,
            Integer duration,
            Integer seed) {
        super(prompt, model, resolution, duration, seed);
        this.firstFrameUrl = firstFrameUrl;
    }
}
