package com.xuejiai.aaf.module.digitalhuman.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 数字人视频VO
 * @author Kiro
 */
@Data
@Schema(description = "数字人视频信息")
public class DigitalHumanVideoVO {

    @Schema(description = "视频ID")
    private Long id;

    @Schema(description = "数字人ID")
    private Long digitalHumanId;

    @Schema(description = "数字人名称")
    private String digitalHumanName;

    @Schema(description = "项目ID")
    private Long projectId;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "视频标题")
    private String title;

    @Schema(description = "视频URL")
    private String videoUrl;

    @Schema(description = "缩略图URL")
    private String thumbnailUrl;

    @Schema(description = "视频时长（秒）")
    private Integer duration;

    @Schema(description = "视频比例", example = "16:9")
    private String aspectRatio;

    @Schema(description = "生成方式", example = "aliyun/hyperframes")
    private String provider;

    @Schema(description = "场景数量")
    private Integer sceneCount;

    @Schema(description = "消耗积分")
    private Integer creditsConsumed;

    @Schema(description = "生成状态", example = "pending/completed/failed")
    private String status;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "完成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;
}
