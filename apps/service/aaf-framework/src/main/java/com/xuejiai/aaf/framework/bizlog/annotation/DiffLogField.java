package com.xuejiai.aaf.framework.bizlog.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 标注在实体字段上，指定该字段在 diff 日志中显示的名称和可选的转换函数。
 *
 * <p>示例：{@code @DiffLogField(name = "用户名", function = "getUserName")}
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface DiffLogField {

    /** diff 日志中显示的字段名。 */
    String name();

    /** 值转换函数名（需注册对应的 IParseFunction）。 */
    String function() default "";
}
