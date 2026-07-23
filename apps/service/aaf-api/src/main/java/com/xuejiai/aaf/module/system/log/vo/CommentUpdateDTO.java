package com.xuejiai.aaf.module.system.log.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 更新评论请求。 */
@Schema(description = "更新评论")
public record CommentUpdateDTO(@Schema(description = "评论内容") String content) {}
