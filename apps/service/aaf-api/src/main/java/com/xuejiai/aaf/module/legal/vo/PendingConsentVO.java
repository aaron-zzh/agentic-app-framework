package com.xuejiai.aaf.module.legal.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户待同意的法律文档列表 VO。
 *
 * <p>登录后调用 {@code GET /api/legal/consent/pending} 获取，前端据此弹窗强制用户重新确认。
 *
 * @author AaronZZH &amp; Kiro
 */
@Schema(description = "待同意法律文档列表")
public record PendingConsentVO(
        @Schema(description = "待同意项数量") int count,
        @Schema(description = "待同意文档列表") List<LegalDocumentVO> items) {}
