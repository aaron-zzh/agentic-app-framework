package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 参考生视频请求（多张参考图 + prompt，prompt 中用 [Image N] 指代）。 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReferenceToVideoRequest extends VideoBaseRequest {

    /** 参考图片 URL 列表，1~9 张。 */
    private List<String> referenceImageUrls;

    /** 画面比例，如 "16:9"。 */
    private String ratio;

    public ReferenceToVideoRequest(
            String prompt,
            List<String> referenceImageUrls,
            String model,
            String resolution,
            String ratio,
            Integer duration,
            Integer seed) {
        super(prompt, model, resolution, duration, seed);
        this.referenceImageUrls = referenceImageUrls;
        this.ratio = ratio;
    }
}
