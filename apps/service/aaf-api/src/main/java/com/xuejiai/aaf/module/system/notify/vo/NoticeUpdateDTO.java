package com.xuejiai.aaf.module.system.notify.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新通知公告请求。
 *
 * @author AaronZZH & Kiro
 */
public record NoticeUpdateDTO(
        @Schema(description = "标题") String title,
        @Schema(description = "内容") String content,
        @Schema(description = "类型：NOTICE/ANNOUNCEMENT") String type) {}
