/**
 * AI Role（能力配置）管理接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.role;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AI 角色管理 - Role")
@RestController
@RequestMapping("/api/ai/roles")
@RequiredArgsConstructor
public class AiRoleController
        extends BaseCrudController<Role, RoleVO, RoleCreateDTO, RoleCreateDTO, PageParam> {

    private final AiRoleCrudService aiRoleCrudService;

    @Override
    protected AiRoleCrudService getService() {
        return aiRoleCrudService;
    }

    @Operation(
            summary = "发布用户的私有角色为公开角色（复制为新的 PUBLIC 角色）",
            description = "通用 EntityAction 仅提交 {id}，code 缺省由服务端生成")
    @PostMapping("/actions/publish")
    @PreAuthorize("hasPermission(null, 'system:role:create')")
    public Result<RoleVO> publish(@Validated @RequestBody PublishRoleDTO request) {
        return Result.success(aiRoleCrudService.publish(request));
    }
}
