package com.xuejiai.aaf.module.system.user.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户响应
 *
 * @author AaronZZH & Kiro
 */
public record UserVO(
        @Schema(description = "用户 ID", example = "1") Long id,
        @Schema(description = "用户名", example = "admin") String username,
        @Schema(description = "昵称", example = "管理员") String nickname,
        @Schema(description = "邮箱") String email,
        @Schema(description = "手机号") String phone,
        @Schema(description = "头像 URL") String avatar,
        @Schema(description = "状态（0 正常 / 1 禁用）", example = "0") Integer status,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "更新时间") LocalDateTime updateTime) {}
