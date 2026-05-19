package com.xuejiai.aaf.config;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/** 租户过滤器，从请求头 X-Org-Id 读取当前组织 ID 并存入 TenantContext。 */
@Component
@Order(200) // Security Filter 之后
public class TenantFilter implements Filter {

    private static final String HEADER_ORG_ID = "X-Org-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            var httpRequest = (HttpServletRequest) request;
            var orgIdHeader = httpRequest.getHeader(HEADER_ORG_ID);
            if (orgIdHeader != null && !orgIdHeader.isBlank()) {
                TenantContext.setCurrentOrgId(Long.valueOf(orgIdHeader));
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
