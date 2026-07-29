package com.xuejiai.aaf.module.content.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目资料引用分页查询。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "项目资料引用分页查询")
public class ContentProjectProfileRefPageDTO extends PageParam {

    @Schema(description = "projectId 筛选")
    private Long projectId;
}
