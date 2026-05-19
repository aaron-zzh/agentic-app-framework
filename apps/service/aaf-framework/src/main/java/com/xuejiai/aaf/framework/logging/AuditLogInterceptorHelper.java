package com.xuejiai.aaf.framework.logging;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 为 JPA EntityListener 提供 ApplicationEventPublisher 的静态访问。
 *
 * <p>JPA EntityListener 不支持依赖注入，通过此 Helper 在 Spring 初始化时注入。
 */
@Component
public class AuditLogInterceptorHelper {

    private static ApplicationEventPublisher publisher;

    public AuditLogInterceptorHelper(ApplicationEventPublisher publisher) {
        AuditLogInterceptorHelper.publisher = publisher;
    }

    public static ApplicationEventPublisher getPublisher() {
        return publisher;
    }
}
