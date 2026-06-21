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
    ErrorCode AUTH_EMAIL_ALREADY_REGISTERED = ErrorCode.of(1_000_003, "该邮箱已注册");
    ErrorCode AUTH_PHONE_ALREADY_REGISTERED = ErrorCode.of(1_000_008, "该手机号已注册");
    ErrorCode AUTH_VERIFY_CODE_INVALID = ErrorCode.of(1_000_004, "验证码无效或已过期");
    ErrorCode AUTH_VERIFY_CODE_RATE_LIMIT = ErrorCode.of(1_000_005, "发送过于频繁，请稍后再试");
    ErrorCode AUTH_USER_LOCKED = ErrorCode.of(1_000_006, "账号已锁定，请稍后再试");
    ErrorCode AUTH_EMAIL_NOT_VERIFIED = ErrorCode.of(1_000_007, "邮箱未验证");
    ErrorCode AUTH_REGISTER_IP_RATE_LIMIT = ErrorCode.of(1_000_009, "注册过于频繁，请稍后再试");
    ErrorCode AUTH_VERIFY_CODE_SEND_FAILED = ErrorCode.of(1_000_010, "验证码发送失败，请稍后重试");

    // ========== USER 模块 1_001_000 ==========
    ErrorCode USER_NOT_FOUND = ErrorCode.of(1_001_000, "用户不存在");
    ErrorCode USER_USERNAME_EXISTS = ErrorCode.of(1_001_001, "用户名已存在");
    ErrorCode USER_PASSWORD_INCORRECT = ErrorCode.of(1_001_002, "旧密码不正确");
    ErrorCode USER_ADMIN_DELETE_FORBIDDEN = ErrorCode.of(1_001_003, "不允许删除管理员");

    // ========== OAUTH 模块 1_000_100 ==========
    ErrorCode OAUTH_PROVIDER_NOT_CONFIGURED = ErrorCode.of(1_000_100, "该 OAuth 提供商未配置");
    ErrorCode OAUTH_EXCHANGE_FAILED = ErrorCode.of(1_000_101, "OAuth 授权码换取用户信息失败");
    ErrorCode OAUTH_ALREADY_BOUND = ErrorCode.of(1_000_102, "该第三方账号已绑定其他用户");
    ErrorCode OAUTH_NOT_BOUND = ErrorCode.of(1_000_103, "未绑定该第三方账号");

    // ========== CHAT 模块 1_003_000 ==========
    ErrorCode CHAT_SESSION_NOT_FOUND = ErrorCode.of(1_003_000, "聊天会话不存在");
    ErrorCode CHAT_MESSAGE_NOT_FOUND = ErrorCode.of(1_003_001, "聊天消息不存在");

    // ========== AI MODEL 模块 1_004_000 ==========
    ErrorCode AI_MODEL_NOT_FOUND = ErrorCode.of(1_004_000, "AI 模型不存在");
    ErrorCode AI_MODEL_ID_EXISTS = ErrorCode.of(1_004_001, "模型 ID 已存在");
    ErrorCode AI_MODEL_PROVIDER_NOT_SUPPORTED = ErrorCode.of(1_004_002, "不支持的模型协议类型");
    ErrorCode AI_MODEL_DISABLED = ErrorCode.of(1_004_003, "模型已禁用");
    ErrorCode AI_MODEL_IMPORT_INVALID = ErrorCode.of(1_004_004, "模型导入文件格式不正确");
}
