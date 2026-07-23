package com.xuejiai.aaf.framework.engine.entitlement;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权益配额检查注解——与四层权限（RBAC/ReBAC/记录规则/ABAC）平行的第五层商业权益检查。
 *
 * <p>判定顺序：先 RBAC（@PreAuthorize）→ 再 @Entitlement → 执行方法 → 方法成功后扣减留痕。
 *
 * <p>用法示例：
 *
 * <pre>{@code
 * @PreAuthorize("hasPermission('ai:chat')")
 * @Entitlement(code = "ai_token", cost = "10")
 * public ChatResponse chat(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Entitlement {

    /** 权益编码（对应 entitlement_def.code） */
    String code();

    /** 消耗额度，仅允许数字常量；BOOLEAN 类型权益此值忽略（仅检查是否拥有）。 */
    String cost() default "1";
}
