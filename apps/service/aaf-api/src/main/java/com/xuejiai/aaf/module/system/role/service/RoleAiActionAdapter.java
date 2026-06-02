package com.xuejiai.aaf.module.system.role.service;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.intelligent.action.BaseCrudEntityActionAdapter;
import com.xuejiai.aaf.module.system.role.domain.Role;
import com.xuejiai.aaf.module.system.role.vo.RoleCreateDTO;
import com.xuejiai.aaf.module.system.role.vo.RolePageParam;
import com.xuejiai.aaf.module.system.role.vo.RoleUpdateDTO;
import com.xuejiai.aaf.module.system.role.vo.RoleVO;

/** 角色实体 AI 标准动作适配器。 */
@Component
public class RoleAiActionAdapter
        extends BaseCrudEntityActionAdapter<
                Role, RoleVO, RoleCreateDTO, RoleUpdateDTO, RolePageParam> {

    private final RoleService roleService;

    public RoleAiActionAdapter(ObjectMapper objectMapper, RoleService roleService) {
        super(objectMapper, RoleCreateDTO.class, RoleUpdateDTO.class, RolePageParam.class);
        this.roleService = roleService;
    }

    @Override
    protected BaseCrudService<Role, RoleVO, RoleCreateDTO, RoleUpdateDTO, RolePageParam>
            getService() {
        return roleService;
    }
}
