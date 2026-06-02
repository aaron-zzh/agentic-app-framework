package com.xuejiai.aaf.framework.engine.entitlement;

import java.lang.annotation.*;

/**
 * 权益配额检查注解——与四层权限（RBAC/ReBAC/记录规则/ABAC）平行的第五层商业权益检查。
 *
 * <p>判定顺序：先 RBAC（@PreAuthorize）→ 再 @Entitlement → 执行方法 → 方法成功后扣减留痕。
 *
 * <p>用法示例：
 *
 * <pre>{@code
 * @PreAuthorize("hasPermission('ai:chat')")
 * @Entitlement(code = "ai_token", cost = "#tokens")
 * public ChatResponse chat(@P("tokens") int tokens, ...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Entitlement {

    /** 权益编码（对应 entitlement_def.code） */
    String code();

    /**
     * 消耗额度，支持 SpEL 表达式引用方法参数。
     *
     * <p>BOOLEAN 类型权益此值忽略（仅检查是否拥有）。
     */
    String cost() default "1";
}
