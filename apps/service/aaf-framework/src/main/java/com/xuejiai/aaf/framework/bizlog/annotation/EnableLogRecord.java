package com.xuejiai.aaf.framework.bizlog.annotation;

import java.lang.annotation.*;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import com.xuejiai.aaf.framework.bizlog.support.LogRecordConfigureSelector;

/**
 * 启用操作日志功能。在 Spring Boot 自动配置类上使用此注解， 或在 @Configuration 类上手动声明以启用 @LogRecord AOP 拦截。
 *
 * <p>示例：
 *
 * <pre>{@code
 * @EnableLogRecord(tenant = "your-app")
 * @AutoConfiguration
 * public class BizLogConfiguration { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(LogRecordConfigureSelector.class)
public @interface EnableLogRecord {

    /** 租户标识，用于多租户场景下区分日志来源。 */
    String tenant();

    /** 是否强制使用 CGLIB 代理（默认 false，目标类实现接口时使用 JDK 动态代理）。 */
    boolean proxyTargetClass() default false;

    /** 代理模式，默认 PROXY。 */
    AdviceMode mode() default AdviceMode.PROXY;

    /** 是否与业务事务共用同一个事务（默认 false，日志记录独立于业务事务）。 */
    boolean joinTransaction() default false;

    /** AOP Advisor 优先级，默认最低。 */
    int order() default Ordered.LOWEST_PRECEDENCE;
}
