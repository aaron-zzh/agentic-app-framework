package com.xuejiai.aaf.framework.security.access;

/**
 * Layer 3 服务层权限检查器——业务逻辑内嵌的权限判断。
 *
 * <p>在 Service 方法内调用，处理需要查库才能判断的权限（如"只能操作自己的资源"）。 如果 Layer 1/2 已处理相同维度的权限，本层自动跳过。
 */
public interface ServicePermissionChecker {

    /**
     * 检查用户是否有权操作指定资源。
     *
     * @param userId 当前用户
     * @param resourceType 资源类型（如 "session", "assistant"）
     * @param resourceId 资源 ID
     * @param action 操作（如 "read", "write", "delete"）
     * @return true=有权限
     */
    boolean check(Long userId, String resourceType, String resourceId, String action);

    /** 检查并抛异常（无权限时）。 */
    default void require(Long userId, String resourceType, String resourceId, String action) {
        if (!check(userId, resourceType, resourceId, action)) {
            throw new com.xuejiai.aaf.common.exception.BusinessException(
                    com.xuejiai.aaf.common.exception.GlobalErrorCode.FORBIDDEN);
        }
    }
}
