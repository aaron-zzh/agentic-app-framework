package com.xuejiai.aaf.module.device.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.xuejiai.aaf.module.device.service.DeviceCredentialService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 设备凭据认证过滤器。认证主体固定只授予 {@code ROLE_DEVICE}。 */
public final class DeviceAuthFilter extends OncePerRequestFilter {

    public static final String DEVICE_KEY_HEADER = "X-Device-Key";
    public static final String DEVICE_CREDENTIAL_HEADER = "X-Device-Credential";

    private static final String DEVICE_API_PREFIX = "/api/device";
    private static final String PAIR_API_PREFIX = "/api/device/pair/";
    private static final SimpleGrantedAuthority DEVICE_AUTHORITY =
            new SimpleGrantedAuthority("ROLE_DEVICE");

    private final DeviceCredentialService credentialService;

    public DeviceAuthFilter(DeviceCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return path.startsWith(PAIR_API_PREFIX)
                || !(DEVICE_API_PREFIX.equals(path) || path.startsWith(DEVICE_API_PREFIX + "/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var deviceKey = request.getHeader(DEVICE_KEY_HEADER);
        var credential = request.getHeader(DEVICE_CREDENTIAL_HEADER);
        if (deviceKey == null && credential == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (deviceKey == null || credential == null) {
            reject(response);
            return;
        }

        var principal = credentialService.authenticate(deviceKey, credential);
        if (principal.isEmpty()) {
            SecurityContextHolder.clearContext();
            reject(response);
            return;
        }

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal.get(), null, List.of(DEVICE_AUTHORITY));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":10400005,\"message\":\"设备凭据无效\",\"data\":null}");
    }
}
