package com.xuejiai.aaf.framework.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要审计的实体类。
 *
 * <p>被标记的实体在 INSERT/UPDATE/DELETE 时自动记录字段级变更到审计日志。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /** 排除不审计的字段名（如 updateTime、version） */
    String[] excludeFields() default {"updateTime", "version"};
}
