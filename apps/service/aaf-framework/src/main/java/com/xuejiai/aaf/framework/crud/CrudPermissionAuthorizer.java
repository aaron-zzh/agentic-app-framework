package com.xuejiai.aaf.framework.crud;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.security.access.AccessDecisionService;

import lombok.RequiredArgsConstructor;

/**
 * 通用 CRUD 动态权限解析器，供 BaseCrudController 的 @PreAuthorize 调用。
 *
 * <p>权限解析规则：
 *
 * <ul>
 *   <li>permissionCode 非空 → 检查用户是否持有该功能权限码（L1 功能权限）
 *   <li>permissionCode 为 null 或空 → 降级为仅需登录（isAuthenticated），行级隔离由 L3 数据规则保障
 * </ul>
 */
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
        // 无权限码：降级为仅登录校验，行级数据隔离由 sys_data_access_rule 保障
        if (permissionCode == null || permissionCode.isBlank()) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.isAuthenticated();
        }
        return accessDecisionService.hasPermission(permissionCode);
    }
}
