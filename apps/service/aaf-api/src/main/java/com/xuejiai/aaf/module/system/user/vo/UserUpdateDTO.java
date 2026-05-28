package com.xuejiai.aaf.module.system.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新用户请求。
 *
 * @author AaronZZH & Kiro
 */
public record UserUpdateDTO(
        @Size(max = 100, message = "昵称最长 100 字符") @Schema(description = "昵称") String nickname,
        Integer status) {}
