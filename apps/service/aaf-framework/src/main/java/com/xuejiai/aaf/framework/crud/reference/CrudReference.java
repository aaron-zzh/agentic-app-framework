package com.xuejiai.aaf.framework.crud.reference;

import static com.xuejiai.aaf.framework.crud.reference.ReferenceCapability.READ;
import static com.xuejiai.aaf.framework.crud.reference.ReferenceCapability.REFERENCE;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明实体上的固定资源引用或 resource+id 多态引用。 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(CrudReferences.class)
public @interface CrudReference {

    String key() default "";

    String idProperty() default "";

    String targetResource() default "";

    String resourceProperty() default "";

    String inputField() default "";

    String viewField() default "";

    String additionalPolicyBean() default "";

    ReferenceCapability[] capabilities() default {READ, REFERENCE};
}
