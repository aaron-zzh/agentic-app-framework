package com.xuejiai.aaf.framework.logging;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** HTTP 请求指标过滤器，记录请求计数和响应时间。 */
@Component
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final MeterRegistry registry;

    public RequestMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var sample = Timer.start(registry);
        try {
            chain.doFilter(request, response);
        } finally {
            var path = normalizePath(request.getRequestURI());
            sample.stop(Timer.builder("aaf_http_requests")
                    .tag("method", request.getMethod())
                    .tag("path", path)
                    .tag("status", String.valueOf(response.getStatus()))
                    .description("HTTP 请求耗时")
                    .register(registry));
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        // 排除 actuator 和静态资源
        return path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs");
    }

    /** 路径归一化：将路径参数替换为占位符，避免高基数标签。 */
    private String normalizePath(String uri) {
        // 将数字 ID 替换为 {id}
        return uri.replaceAll("/\\d+", "/{id}");
    }
}
