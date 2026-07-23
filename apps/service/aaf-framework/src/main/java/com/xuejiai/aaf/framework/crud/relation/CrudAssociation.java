package com.xuejiai.aaf.framework.crud.relation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.xuejiai.aaf.common.model.BaseEntity;

/** 声明轻量关联实体或子实体对应的 CRUD 关系。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CrudAssociation {

    Class<? extends BaseEntity> sourceEntity();

    String key();

    AssociationKind kind();

    String sourceProperty();

    String targetProperty();

    String targetResource();

    String inputField() default "";

    String viewField() default "";

    RelationDefinition.SyncMode syncMode() default RelationDefinition.SyncMode.READ_ONLY;

    int maxCardinality() default 100;

    String additionalPolicyBean() default "";
}
