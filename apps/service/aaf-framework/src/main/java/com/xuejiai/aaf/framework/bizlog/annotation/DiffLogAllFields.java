package com.xuejiai.aaf.framework.bizlog.annotation;

import java.lang.annotation.*;

/** 标注在实体类上，对所有未标注 @DIffLogIgnore 的字段自动生成 diff 日志。 不需要在每个字段上单独加 @DiffLogField。 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DiffLogAllFields {}
