package com.xuejiai.aaf.module.ai.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员发布私有角色为公开角色的请求。
 *
 * <p>通用 EntityAction 只提交 {@code {id}}，所以 {@code code} 与 {@code name} 都是可选的： 缺省时 code
 * 由服务端生成，名称沿用私有角色名称。
 */
@Schema(description = "发布公开角色请求")
public record PublishRoleDTO(
        @Schema(description = "要发布的私有角色 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
                Long id,
        @Schema(description = "公开角色的 code，必须以 public.role. 开头；缺省由服务端生成") @Size(max = 64)
                String code,
        @Schema(description = "公开角色的名称，缺省沿用私有角色名称") @Size(max = 128) String name) {}
