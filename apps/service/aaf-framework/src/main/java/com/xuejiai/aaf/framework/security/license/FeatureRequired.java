package com.xuejiai.aaf.framework.security.license;

import java.lang.annotation.*;

/** 标记需要特定商业 feature 授权的类或方法。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureRequired {
    /** 高级模块/能力码。 */
    String value();
}
