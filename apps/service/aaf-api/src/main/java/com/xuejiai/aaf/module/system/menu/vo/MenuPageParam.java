package com.xuejiai.aaf.module.system.menu.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 菜单分页查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "菜单分页查询参数")
public class MenuPageParam extends PageParam {

    @Schema(description = "关键词，匹配标题/路径/权限码")
    private String keyword;

    @Schema(description = "父级菜单 ID")
    private Long parentId;

    @Schema(description = "菜单类型：GROUP/MENU/BUTTON")
    private String menuType;

    @Schema(description = "是否可见")
    private Boolean visible;
}
