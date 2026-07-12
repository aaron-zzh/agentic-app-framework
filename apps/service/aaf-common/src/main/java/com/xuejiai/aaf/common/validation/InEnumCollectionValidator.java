package com.xuejiai.aaf.common.validation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link InEnum} 集合校验器，用于 {@code List<String>}/{@code Set<Integer>} 等多值字段。
 *
 * @author AaronZZH & Kiro
 */
public class InEnumCollectionValidator implements ConstraintValidator<InEnum, Collection<?>> {

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
    public boolean isValid(Collection<?> value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (values.containsAll(value)) {
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
