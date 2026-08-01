package com.xuejiai.aaf.framework.security.apikey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * API Key 认证过滤器——与 JWT 并列，优先级更高。
 *
 * <p>识别 Header: {@code Authorization: Bearer aaf_dk_xxx} 或 {@code X-API-Key: aaf_dk_xxx}。
 * 认证成功后将用户信息设入 SecurityContext，后续流程与 JWT 认证一致。
 */
@Slf4j
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "aaf_dk_";
    private static final java.time.Duration LAST_USED_UPDATE_INTERVAL =
            java.time.Duration.ofMinutes(1);

    private final ApiKeyRepository apiKeyRepository;
    private final org.springframework.beans.factory.ObjectProvider<ApiKeyUserRoleProvider>
            roleProvider;

    public ApiKeyAuthFilter(
            @Lazy ApiKeyRepository apiKeyRepository,
            org.springframework.beans.factory.ObjectProvider<ApiKeyUserRoleProvider> roleProvider) {
        this.apiKeyRepository = apiKeyRepository;
        this.roleProvider = roleProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var rawKey = extractKey(request);
        if (rawKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        var hash = sha256(rawKey);
        var apiKeyOpt = apiKeyRepository.findByKeyHashAndEnabledTrue(hash);

        if (apiKeyOpt.isEmpty() || !apiKeyOpt.get().isValid()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid or expired API Key\"}");
            return;
        }

        var apiKey = apiKeyOpt.get();

        // 设置 SecurityContext（principal = userId 字符串，与 JWT 一致）
        var auth =
                new UsernamePasswordAuthenticationToken(
                        apiKey.getUserId().toString(), null, authoritiesOf(apiKey));
        auth.setDetails(apiKey); // 可通过 details 获取 ApiKey 对象
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 分钟级更新最后使用时间，避免每次认证都同步写库
        var now = Instant.now();
        if (apiKey.getLastUsedAt() == null
                || apiKey.getLastUsedAt().plus(LAST_USED_UPDATE_INTERVAL).isBefore(now)) {
            apiKey.setLastUsedAt(now);
            apiKeyRepository.save(apiKey);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * M9：授予 {@code ROLE_API_KEY} + 绑定用户的真实角色。
     *
     * <p>原实现只给 {@code ROLE_API_KEY}，API Key 到不了任何需要业务角色的端点。现在继承绑定用户的角色， 使 API Key
     * 与该用户自己登录时具备一致的角色视图；{@code ROLE_API_KEY} 保留，供需要区分"人 vs 密钥" 的策略使用，ApiKey 自身的 scope /
     * allowedTables 收窄约束不受影响，仍在各自校验点生效。
     *
     * <p>角色查询由 aaf-api 提供实现（{@link ApiKeyUserRoleProvider}）；未提供实现或查询异常时退回只授 {@code ROLE_API_KEY}，保持
     * fail-closed。
     */
    private List<org.springframework.security.core.GrantedAuthority> authoritiesOf(ApiKey apiKey) {
        var authorities =
                new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_API_KEY"));
        var provider = roleProvider.getIfAvailable();
        if (provider == null) {
            return authorities;
        }
        try {
            provider.roleCodesOf(apiKey.getUserId()).stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(code -> new SimpleGrantedAuthority("ROLE_" + code.trim().toUpperCase()))
                    .forEach(authorities::add);
        } catch (RuntimeException e) {
            log.warn("API Key 角色继承失败，退回仅 ROLE_API_KEY: userId={}", apiKey.getUserId(), e);
        }
        return authorities;
    }

    private String extractKey(HttpServletRequest request) {
        // 优先 X-API-Key header
        var xApiKey = request.getHeader("X-API-Key");
        if (xApiKey != null && xApiKey.startsWith(KEY_PREFIX)) {
            return xApiKey;
        }
        // 其次 Authorization: Bearer aaf_dk_xxx
        var auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer " + KEY_PREFIX)) {
            return auth.substring(7);
        }
        return null;
    }

    static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (var b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
