package com.xuejiai.aaf.module.system;

import com.xuejiai.aaf.common.exception.ErrorCode;

/**
 * System 模块错误码，使用 1_000_000 ~ 1_999_999 段。
 *
 * <p>子模块分段：
 *
 * <ul>
 *   <li>AUTH：1_000_000 ~ 1_000_999
 *   <li>USER：1_001_000 ~ 1_001_999
 *   <li>MENU：1_002_000 ~ 1_002_999
 * </ul>
 */
public interface ErrorCodeConstants {

    // ========== AUTH 模块 1_000_000 ==========
    ErrorCode AUTH_LOGIN_BAD_CREDENTIALS = ErrorCode.of(1_000_000, "登录失败，账号密码不正确");
    ErrorCode AUTH_LOGIN_USER_DISABLED = ErrorCode.of(1_000_001, "登录失败，账号被禁用");
    ErrorCode AUTH_TOKEN_EXPIRED = ErrorCode.of(1_000_002, "Token 无效或已过期");

    // ========== USER 模块 1_001_000 ==========
    ErrorCode USER_NOT_FOUND = ErrorCode.of(1_001_000, "用户不存在");
    ErrorCode USER_USERNAME_EXISTS = ErrorCode.of(1_001_001, "用户名已存在");
    ErrorCode USER_PASSWORD_INCORRECT = ErrorCode.of(1_001_002, "旧密码不正确");
    ErrorCode USER_ADMIN_DELETE_FORBIDDEN = ErrorCode.of(1_001_003, "不允许删除管理员");
}
