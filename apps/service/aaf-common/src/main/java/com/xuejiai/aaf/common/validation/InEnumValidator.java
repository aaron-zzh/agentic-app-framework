package com.xuejiai.aaf.common.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link InEnum} 单值校验器。
 *
 * @author AaronZZH & Kiro
 */
public class InEnumValidator implements ConstraintValidator<InEnum, Object> {

    private List<?> values;

    @Override
    public void initialize(InEnum annotation) {
        ArrayValuable<?>[] enumConstants = annotation.value().getEnumConstants();
        this.values =
                enumConstants.length == 0
                        ? Collections.emptyList()
                        : Arrays.asList(enumConstants[0].array());
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        // 为空时不校验，交由 @NotNull/@NotBlank 处理
        if (value == null) {
            return true;
        }
        if (values.contains(value)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        context.getDefaultConstraintMessageTemplate()
                                .replace("{value}", values.toString()))
                .addConstraintViolation();
        return false;
    }
}
