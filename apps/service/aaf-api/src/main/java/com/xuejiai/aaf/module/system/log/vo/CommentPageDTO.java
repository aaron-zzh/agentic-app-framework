package com.xuejiai.aaf.module.system.log.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 评论分页查询请求。父资源标识由嵌套路由注入。 */
@Schema(description = "评论分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentPageDTO extends PageParam {

    @Schema(hidden = true)
    private String entityType;

    @Schema(hidden = true)
    private Long entityId;
}
