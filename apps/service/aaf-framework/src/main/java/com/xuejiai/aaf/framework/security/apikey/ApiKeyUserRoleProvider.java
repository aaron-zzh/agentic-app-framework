package com.xuejiai.aaf.framework.security.apikey;

import java.util.List;

/**
 * API Key 绑定用户的角色查询 SPI（M9）。
 *
 * <p>修复前：{@link ApiKeyAuthFilter} 只授予 {@code ROLE_API_KEY}，不继承绑定用户的真实角色—— API Key
 * 调用方到不了任何需要业务角色的端点（fail-closed，不是越权，但能力与预期不符）。
 *
 * <p>framework 层拿不到 {@code UserRoleRepository}（在 aaf-api），故以最小 SPI 反转依赖：由 aaf-api 提供实现， 复用登录签发 JWT
 * 时的同一套角色查询，避免两处各写一份。未提供实现时过滤器退回只授 {@code ROLE_API_KEY}。
 */
public interface ApiKeyUserRoleProvider {

    /**
     * 查询用户的角色编码（不含 {@code ROLE_} 前缀，如 {@code admin}/{@code member}）。
     *
     * @return 角色编码列表；无角色返回空列表
     */
    List<String> roleCodesOf(Long userId);
}
