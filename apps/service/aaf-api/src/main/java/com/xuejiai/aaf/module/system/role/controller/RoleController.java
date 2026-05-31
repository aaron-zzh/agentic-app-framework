package com.xuejiai.aaf.module.system.role.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.system.role.domain.Role;
import com.xuejiai.aaf.module.system.role.service.RoleService;
import com.xuejiai.aaf.module.system.role.vo.RoleCreateDTO;
import com.xuejiai.aaf.module.system.role.vo.RolePageParam;
import com.xuejiai.aaf.module.system.role.vo.RoleUpdateDTO;
import com.xuejiai.aaf.module.system.role.vo.RoleVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 角色管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController
        extends BaseCrudController<Role, RoleVO, RoleCreateDTO, RoleUpdateDTO, RolePageParam> {

    private final RoleService roleService;

    @Override
    protected RoleService getService() {
        return roleService;
    }
}
