package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 内容产出响应 VO。 */
@Data
public class AigcContentVO {
    private Long id;
    private Long projectId;
    private String type;
    private String title;
    private Long docId;
    private String assetIds;
    private String platform;
    private String publishStatus;
    private LocalDateTime publishTime;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
