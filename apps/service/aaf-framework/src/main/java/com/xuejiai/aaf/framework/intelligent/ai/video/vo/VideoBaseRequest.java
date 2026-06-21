package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 视频生成请求基类，提取各子类公共字段。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoBaseRequest {

    private String prompt;
    private String model;
    private String resolution;
    private Integer duration;
    private Integer seed;
}
