package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 视频编辑请求。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VideoEditApiRequest extends VideoBaseRequest {

    private String videoUrl;
    private List<String> referenceImageUrls;
    private String audioSetting;

    public VideoEditApiRequest(
            String prompt,
            String videoUrl,
            List<String> referenceImageUrls,
            String model,
            String resolution,
            String audioSetting,
            Integer seed) {
        super(prompt, model, resolution, null, seed);
        this.videoUrl = videoUrl;
        this.referenceImageUrls = referenceImageUrls;
        this.audioSetting = audioSetting;
    }
}
