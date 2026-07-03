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
 *   <li>permissionCode 已在系统中注册（{@code sys_permission_code} 存在记录）→ 严格校验用户是否持有该功能权限码（L1 功能权限）
 *   <li>permissionCode 为空或未注册 → 降级为仅需登录（isAuthenticated），行级隔离由 L3 数据规则保障
 * </ul>
 *
 * <p>用"是否已注册"而非"字符串是否为空"判断降级，因为 {@code BaseCrudService#permissionCode} 总会拼接出
 * 非空字符串；只有业务实体已显式补充权限码种子数据时才代表接入了精细权限管控，未接入的实体维持仅登录语义，
 * 避免大量未配置权限码的业务实体被误锁。
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
        // 权限码未注册：降级为仅登录校验，行级数据隔离由 sys_data_access_rule 保障
        if (permissionCode == null
                || permissionCode.isBlank()
                || !accessDecisionService.isPermissionCodeRegistered(permissionCode)) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.isAuthenticated();
        }
        return accessDecisionService.hasPermission(permissionCode);
    }
}
