package com.xuejiai.aaf.framework.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 诊断用 AccessDeniedHandler——403 时打印当前 token 的 authorities 与 JWT roles claim。
 *
 * <p>用于定位「角色已绑定 super_admin 但接口仍 403」类问题。日志含敏感信息（用户 ID、角色）， 仅建议在 dev / 排查阶段开启 INFO 级别；生产可降为 DEBUG。
 *
 * @author AaronZZH &amp; Kiro
 */
@Slf4j
@Component
public class LoggingAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String principal = auth == null ? "<null>" : String.valueOf(auth.getName());
        String authorities = auth == null ? "<null>" : String.valueOf(auth.getAuthorities());
        String jwtRoles = "<not-jwt>";
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt token = jwtAuth.getToken();
            jwtRoles = String.valueOf(token.getClaimAsStringList("roles"));
        }
        log.warn(
                "[AccessDenied] {} {} principal={} authorities={} jwtRoles={} reason={}",
                request.getMethod(),
                request.getRequestURI(),
                principal,
                authorities,
                jwtRoles,
                accessDeniedException.getMessage());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"Forbidden\",\"data\":null}");
    }
}
