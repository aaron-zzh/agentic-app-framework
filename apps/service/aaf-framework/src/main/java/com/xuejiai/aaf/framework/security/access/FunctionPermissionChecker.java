package com.xuejiai.aaf.framework.security.access;

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
}
