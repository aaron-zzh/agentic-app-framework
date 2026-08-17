package com.xuejiai.aaf.module.ai.skill;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Skill 根对象分页参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillPageDTO extends PageParam {

    @Size(max = 32)
    private String locale;

    @Pattern(regexp = "PRIVATE|WORKSPACE|PUBLIC")
    private String visibility;

    private Boolean builtIn;
    private Boolean publishedOnly;

    @Schema(hidden = true)
    private Boolean ownerOnly;
}
