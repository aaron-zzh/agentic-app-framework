package com.xuejiai.aaf.framework.security.access;

/**
 * 关系权限检查 SPI。
 *
 * <p>用于 Spring Security {@code hasPermission(#id, 'document', 'can_read')} 这类对象级权限判断。
 */
public interface RelationPermissionChecker {

    /**
     * 检查用户是否通过关系元组拥有指定对象权限。
     *
     * @param userId 数据归属用户 ID
     * @param objectType 对象类型
     * @param objectId 对象 ID
     * @param permission 关系权限，如 can_read/can_write/can_delete
     * @return true=允许
     */
    boolean hasPermission(Long userId, String objectType, String objectId, String permission);
}
