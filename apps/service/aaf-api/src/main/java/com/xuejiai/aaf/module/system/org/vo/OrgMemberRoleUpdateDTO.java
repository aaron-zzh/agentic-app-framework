package com.xuejiai.aaf.module.system.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 修改成员角色请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "修改成员角色请求")
public record OrgMemberRoleUpdateDTO(
        @NotBlank(message = "角色不能为空") @Schema(description = "新角色", example = "admin")
                String role) {}
