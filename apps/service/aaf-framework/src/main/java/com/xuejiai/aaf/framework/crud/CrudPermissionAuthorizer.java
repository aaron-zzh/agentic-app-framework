package com.xuejiai.aaf.framework.crud;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.security.access.AccessDecisionService;

import lombok.RequiredArgsConstructor;

/** 通用 CRUD 动态权限解析器，供 BaseCrudController 的 @PreAuthorize 调用。 */
@Component("crudAuth")
@RequiredArgsConstructor
public class CrudPermissionAuthorizer {

    private final AccessDecisionService accessDecisionService;

    public boolean can(Object controller, String action) {
        if (!(controller instanceof BaseCrudController<?, ?, ?, ?, ?> crudController)) {
            return false;
        }
        var service = crudController.getService();
        var permissionCode = service.permissionCode(action);
        return permissionCode != null
                && !permissionCode.isBlank()
                && accessDecisionService.hasPermission(permissionCode);
    }
}
