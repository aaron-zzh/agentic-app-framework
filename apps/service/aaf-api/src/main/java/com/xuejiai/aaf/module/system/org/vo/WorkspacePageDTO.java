package com.xuejiai.aaf.module.system.org.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作区分页查询请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "工作区分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkspacePageDTO extends PageParam {

    @Schema(description = "工作区名称（模糊搜索）")
    private String name;
}
