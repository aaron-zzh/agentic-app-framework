package com.xuejiai.aaf.common.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Servlet 工具类。
 *
 * <p>提供获取客户端 IP、User-Agent 的便捷方法，用于登录日志、审计日志等场景。
 */
@UtilityClass
public class ServletUtils {

    /** 获取当前请求，非 Web 环境返回 null。 */
    public static HttpServletRequest getRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    /**
     * 获取客户端真实 IP，自动处理反向代理（X-Forwarded-For / X-Real-IP 等）。
     *
     * @return IP 地址，非 Web 环境返回 null
     */
    public static String getClientIp() {
        var request = getRequest();
        return request != null ? getClientIp(request) : null;
    }

    public static String getClientIp(HttpServletRequest request) {
        for (var header : new String[]{
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"}) {
            var ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /** 获取 User-Agent，非 Web 环境返回 null。 */
    public static String getUserAgent() {
        var request = getRequest();
        return request != null ? getUserAgent(request) : null;
    }

    public static String getUserAgent(HttpServletRequest request) {
        var ua = request.getHeader("User-Agent");
        return ua != null ? ua : "";
    }
}
