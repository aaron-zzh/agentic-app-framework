package com.xuejiai.aaf.module.system.role.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色分页查询参数。
 *
 * @author AaronZZH & Codex
 */
@Getter
@Setter
@Schema(description = "角色分页查询参数")
public class RolePageParam extends PageParam {

    @Schema(description = "关键词，匹配编码/名称")
    private String keyword;

    @Schema(description = "状态：0 正常 / 1 禁用")
    private Integer status;
}
