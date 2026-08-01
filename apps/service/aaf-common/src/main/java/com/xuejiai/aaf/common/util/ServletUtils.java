package com.xuejiai.aaf.common.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

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
     * 获取客户端真实 IP。
     *
     * <p>m7：不再自行解析 {@code X-Forwarded-For / X-Real-IP / Proxy-Client-IP} 等头——这些头由客户端任意可写，
     * 原实现"命中哪个头就取哪个"等于让调用方自选 IP，导致登录日志、注册来源 IP、审计记录都可被伪造。
     *
     * <p>正确做法是把代理头的可信性交给基础设施层判定：由网关/LB 覆写并剥离客户端伪造的头，应用侧通过 {@code
     * server.forward-headers-strategy}（生产已设为 framework，见 application-prod.yaml）让 Spring 的
     * ForwardedHeaderFilter 统一改写 {@code remoteAddr}。因此这里只取 {@code getRemoteAddr()}： 有可信代理时它已是真实客户端
     * IP，没有时它是直连对端 IP——两种情况都不可伪造。
     *
     * @return IP 地址，非 Web 环境返回 null
     */
    public static String getClientIp() {
        var request = getRequest();
        return request != null ? getClientIp(request) : null;
    }

    public static String getClientIp(HttpServletRequest request) {
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
