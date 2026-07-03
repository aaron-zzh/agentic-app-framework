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

    /**
     * 检查权限码是否已在系统中注册（{@code sys_permission_code} 是否存在对应记录）。
     *
     * <p>用于区分"业务未接入精细权限管控"（未注册，降级为仅登录）与"已接入但用户未被授权"（已注册但 {@link #hasPermission} 返回
     * false，应拒绝）两种场景，避免未补充权限码数据的业务实体被误锁。
     *
     * @param permissionCode 三段式权限码
     * @return true=已注册
     */
    boolean isRegistered(String permissionCode);
}
