package com.xuejiai.aaf.module.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 修改个人信息请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "修改个人信息")
public record UserProfileUpdateDTO(
        @Size(max = 100) @Schema(description = "昵称") String nickname,
        @Size(max = 500) @Schema(description = "头像 URL") String avatar,
        @Email @Schema(description = "邮箱") String email,
        @Size(max = 20) @Schema(description = "手机号") String phone) {}
