/**
 * AI Role（能力配置）管理接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.role;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;

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
}
