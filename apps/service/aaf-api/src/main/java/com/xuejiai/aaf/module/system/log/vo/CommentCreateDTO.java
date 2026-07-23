package com.xuejiai.aaf.module.system.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 创建评论请求。父资源标识由嵌套路由注入。 */
@Schema(description = "创建评论")
public record CommentCreateDTO(
        @Schema(hidden = true) String entityType,
        @Schema(hidden = true) Long entityId,
        @Schema(description = "评论内容") String content) {}
