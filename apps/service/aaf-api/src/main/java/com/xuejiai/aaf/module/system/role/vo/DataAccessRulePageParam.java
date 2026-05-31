package com.xuejiai.aaf.module.system.role.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据权限规则分页查询参数。
 *
 * @author AaronZZH & Codex
 */
@Getter
@Setter
@Schema(description = "数据权限规则分页查询参数")
public class DataAccessRulePageParam extends PageParam {

    @Schema(description = "实体标识，精确匹配")
    private String entitySlug;

    @Schema(description = "效果：allow / deny")
    private String effect;
}
