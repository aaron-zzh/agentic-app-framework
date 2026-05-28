package com.xuejiai.aaf.module.system.notify.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建通知公告请求。
 *
 * @author AaronZZH & Kiro
 */
public record NoticeCreateDTO(
        @Schema(description = "标题") @NotBlank String title,
        @Schema(description = "内容") String content,
        @Schema(description = "类型：NOTICE/ANNOUNCEMENT") @NotBlank String type) {}
