package com.xuejiai.aaf.framework.security.apikey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * API Key 认证过滤器——与 JWT 并列，优先级更高。
 *
 * <p>识别 Header: {@code Authorization: Bearer aaf_dk_xxx} 或 {@code X-API-Key: aaf_dk_xxx}。
 * 认证成功后将用户信息设入 SecurityContext，后续流程与 JWT 认证一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "aaf_dk_";

    private final ApiKeyRepository apiKeyRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
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
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_API_KEY"));
        var auth = new UsernamePasswordAuthenticationToken(
                apiKey.getUserId().toString(), null, authorities);
        auth.setDetails(apiKey); // 可通过 details 获取 ApiKey 对象
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 异步更新最后使用时间
        apiKey.setLastUsedAt(Instant.now());
        apiKeyRepository.save(apiKey);

        filterChain.doFilter(request, response);
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
