package com.xuejiai.aaf.framework.security.license;

import java.lang.annotation.*;

/** 标记仅官方服务 owner 授权可访问的类或方法。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LicenseOwnerRequired {
    /** 功能名称，用于错误提示。 */
    String value() default "官方服务管理";
}
