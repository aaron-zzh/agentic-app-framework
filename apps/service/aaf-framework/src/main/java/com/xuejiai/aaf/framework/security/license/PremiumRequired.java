package com.xuejiai.aaf.framework.security.license;

import java.lang.annotation.*;

/** 标记需要 Premium 授权的类或方法。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PremiumRequired {
    /** 功能名称，用于错误提示。 */
    String value() default "高级功能";
}
