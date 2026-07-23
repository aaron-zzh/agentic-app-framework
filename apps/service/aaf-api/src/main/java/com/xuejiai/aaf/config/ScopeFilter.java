package com.xuejiai.aaf.config;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.scope.ScopeContext;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 查询视角过滤器，从请求头 {@code X-Scope} 读取本次请求是否显式声明查询全部，写入 {@link ScopeContext}。
 *
 * <p>取值 {@code all} 表示显式声明查询全部（不额外收窄，行为由行级数据权限决定）；其余取值（含未携带该头）
 * 均视为默认个人视角——默认值是收窄而非放开（fail-safe），无需归属校验（不同于 {@link OrgFilter} 的组织/工作区归属需要防横向越权）。
 */
@Component
@Order(200) // 与 OrgFilter 同级，顺序互不影响
public class ScopeFilter implements Filter {

    private static final String HEADER_SCOPE = "X-Scope";
    private static final String SCOPE_ALL = "all";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            var httpRequest = (HttpServletRequest) request;
            var scope = httpRequest.getHeader(HEADER_SCOPE);
            ScopeContext.setAllScope(SCOPE_ALL.equalsIgnoreCase(scope));
            chain.doFilter(request, response);
        } finally {
            ScopeContext.clear();
        }
    }
}
