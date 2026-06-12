package com.xuejiai.aaf.module.ai.aigc.task.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AIGC 任务分页查询参数。
 *
 * @author Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AIGC 任务分页查询参数")
public class AigcTaskPageDTO extends PageParam {

    @Schema(description = "用户 ID（内部使用）")
    private Long userId;

    @Schema(description = "任务类型：IMAGE / VIDEO / MODEL_3D / MUSIC")
    private String type;

    @Schema(description = "任务状态：PENDING / RUNNING / SUCCESS / FAIL")
    private String status;

    @Schema(description = "所属项目 ID")
    private Long projectId;
}
