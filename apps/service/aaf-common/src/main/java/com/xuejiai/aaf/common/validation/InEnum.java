package com.xuejiai.aaf.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 校验字段取值是否在指定枚举的合法范围内。
 *
 * <p>适用场景：字段取值是固定的代码状态机（如任务状态、审批状态），只能通过发版修改， 不需要开放给运营在字典后台动态配置。取值范围来自 {@link
 * ArrayValuable#array()}。
 *
 * <p>示例：
 *
 * <pre>{@code
 * @InEnum(value = TodoStatusEnum.class, message = "状态必须是 {value}")
 * private String status;
 * }</pre>
 *
 * @author AaronZZH & Kiro
 */
@Target({
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.ANNOTATION_TYPE,
    ElementType.CONSTRUCTOR,
    ElementType.PARAMETER,
    ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {InEnumValidator.class, InEnumCollectionValidator.class})
public @interface InEnum {

    /** 实现 {@link ArrayValuable} 接口的枚举类。 */
    Class<? extends ArrayValuable<?>> value();

    String message() default "必须在指定范围 {value}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
