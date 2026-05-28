package com.xuejiai.aaf.module.system.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 图形验证码响应。
 *
 * @author AaronZZH & Kiro
 */
public record CaptchaVO(
        @Schema(description = "验证码 ID") String captchaId,
        @Schema(description = "Base64 编码图片") String base64Image) {}
