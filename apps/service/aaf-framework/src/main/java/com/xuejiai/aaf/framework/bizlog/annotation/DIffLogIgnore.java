package com.xuejiai.aaf.framework.bizlog.annotation;

import java.lang.annotation.*;

/** 标注在实体字段上，表示该字段不参与 diff 日志生成。 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DIffLogIgnore {}
