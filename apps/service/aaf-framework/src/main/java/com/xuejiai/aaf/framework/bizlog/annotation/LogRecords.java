package com.xuejiai.aaf.framework.bizlog.annotation;

import java.lang.annotation.*;

/** 允许同一方法上标注多个 @LogRecord。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface LogRecords {
    LogRecord[] value();
}
