package com.xuejiai.aaf.framework.security.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Layer 1 声明式权限注解。
 *
 * <p>标注在 Controller 方法或类上，由 AOP 切面统一处理。 适用于所有接口类型（REST/WebSocket/A2A/MCP/IoT）。
 *
 * <p>示例：
 *
 * <pre>
 * {@code @AccessControl(roles = "admin", feature = "chat")}
 * public SseEmitter run(...) { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessControl {

    /** 要求的角色（任一匹配即可），空数组表示仅需认证 */
    String[] roles() default {};

    /** 功能标识（用于功能开关判断），空字符串表示不检查 */
    String feature() default "";

    /** 限流等级 */
    RateLimit rateLimit() default RateLimit.NORMAL;

    /** 接入渠道限制（空数组表示所有渠道均可） */
    Channel[] channels() default {};

    /** 限流等级 */
    enum RateLimit {
        NONE,
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    /** 接入渠道 */
    enum Channel {
        REST,
        WEBSOCKET,
        AG_UI,
        A2A,
        MCP,
        IOT
    }
}
