package com.xuejiai.aaf.module.system.user.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户个人信息响应。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "用户个人信息")
public record UserProfileVO(
        @Schema(description = "用户 ID") Long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "头像 URL") String avatar,
        @Schema(description = "邮箱") String email,
        @Schema(description = "手机号") String phone,
        @Schema(description = "创建时间") LocalDateTime createTime) {}
