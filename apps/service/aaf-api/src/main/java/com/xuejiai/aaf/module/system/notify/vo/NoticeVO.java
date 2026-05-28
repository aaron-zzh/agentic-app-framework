package com.xuejiai.aaf.module.system.notify.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 通知公告响应。
 *
 * @author AaronZZH & Kiro
 */
public record NoticeVO(
        @Schema(description = "主键 ID") Long id,
        @Schema(description = "标题") String title,
        @Schema(description = "内容") String content,
        @Schema(description = "类型：NOTICE/ANNOUNCEMENT") String type,
        @Schema(description = "状态：0=草稿 1=已发布") Short status,
        @Schema(description = "发布时间") LocalDateTime publishTime,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
