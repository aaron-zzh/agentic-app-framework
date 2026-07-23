package com.xuejiai.aaf.framework.security.authorization;

/**
 * 功能权限检查 SPI。
 *
 * <p>Framework 只定义接口，业务模块负责基于角色、权限码等真实数据实现，避免框架层反向依赖业务表。
 */
public interface FunctionPermissionChecker {

    /**
     * 检查用户是否拥有指定功能权限码。
     *
     * @param userId 数据归属用户 ID
     * @param permissionCode 三段式权限码，如 system:user:create
     * @return true=允许
     */
    boolean hasPermission(Long userId, String permissionCode);

    /**
     * 检查权限码是否已在系统中注册（{@code sys_permission_code} 是否存在对应记录）。
     *
     * <p>L1 权限码是安全契约；标准 CRUD 推导出的权限码未注册时必须拒绝，不允许降级为仅登录。
     *
     * @param permissionCode 三段式权限码
     * @return true=已注册
     */
    boolean isRegistered(String permissionCode);
}
