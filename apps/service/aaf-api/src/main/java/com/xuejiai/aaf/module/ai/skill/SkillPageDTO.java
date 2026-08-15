package com.xuejiai.aaf.module.ai.skill;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 技能分页参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillPageDTO extends PageParam {

    @Size(max = 50)
    private String category;

    private Boolean activeOnly;

    @Schema(hidden = true)
    private Boolean ownerOnly;
}
