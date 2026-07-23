package com.xuejiai.aaf.framework.crud.filter;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/** 解析字段允许的相对时间变量及 ISO 日期时间字面量。 */
public final class DateTimeFilterValueResolver {

    private DateTimeFilterValueResolver() {}

    public static LocalDateTime resolve(
            String value,
            FilterEvaluationContext context,
            Set<DateTimeFilterVariable> allowedVariables) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(allowedVariables, "allowedVariables");
        if (value == null) {
            throw invalidValue();
        }
        var variable = DateTimeFilterVariable.fromToken(value);
        if (variable != null) {
            if (!allowedVariables.contains(variable)) {
                throw invalidValue();
            }
            return variable.resolve(context.now());
        }
        return parseIsoDateTime(value);
    }

    private static LocalDateTime parseIsoDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value).atStartOfDay();
            } catch (DateTimeParseException exception) {
                throw invalidValue();
            }
        }
    }

    private static RuntimeException invalidValue() {
        return exception(GlobalErrorCode.BAD_REQUEST);
    }
}
