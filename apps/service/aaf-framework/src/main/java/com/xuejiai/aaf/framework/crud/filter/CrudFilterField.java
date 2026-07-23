package com.xuejiai.aaf.framework.crud.filter;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 单个可筛选字段的声明，同时包含客户端元数据和服务端执行规则。
 *
 * <p>字段仍必须由资源 Service 显式注册，以形成安全白名单；类型工厂确保操作符元数据与实际校验规则保持一致。
 */
public record CrudFilterField<E>(
        String name,
        CrudFilterRule<E> rule,
        List<CrudFilterOperator> operators,
        List<String> variables) {

    private static final List<CrudFilterOperator> TEXT_OPERATORS =
            List.of(
                    CrudFilterOperator.EQ,
                    CrudFilterOperator.CONTAINS,
                    CrudFilterOperator.STARTS_WITH,
                    CrudFilterOperator.IS_EMPTY,
                    CrudFilterOperator.IS_NOT_EMPTY);
    private static final List<CrudFilterOperator> ENUM_OPERATORS =
            List.of(CrudFilterOperator.EQ, CrudFilterOperator.IN, CrudFilterOperator.NOT_IN);
    private static final List<CrudFilterOperator> NULLABLE_ENUM_OPERATORS =
            List.of(
                    CrudFilterOperator.EQ,
                    CrudFilterOperator.IN,
                    CrudFilterOperator.NOT_IN,
                    CrudFilterOperator.IS_NULL,
                    CrudFilterOperator.IS_NOT_NULL);
    private static final List<CrudFilterOperator> LOCAL_DATE_TIME_OPERATORS =
            List.of(
                    CrudFilterOperator.BETWEEN,
                    CrudFilterOperator.GT,
                    CrudFilterOperator.GTE,
                    CrudFilterOperator.LT,
                    CrudFilterOperator.LTE,
                    CrudFilterOperator.IS_NULL,
                    CrudFilterOperator.IS_NOT_NULL);

    public CrudFilterField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("筛选字段名不能为空");
        }
        Objects.requireNonNull(rule, "rule");
        operators = List.copyOf(operators);
        variables = List.copyOf(variables);
    }

    /** 文本字段。 */
    public static <E> CrudFilterField<E> text(String name) {
        return new CrudFilterField<>(name, CrudFilterRule.text(name), TEXT_OPERATORS, List.of());
    }

    /** 不允许 NULL 判断的枚举或固定字符串字段。 */
    public static <E> CrudFilterField<E> enumValues(String name, Set<String> allowedValues) {
        return new CrudFilterField<>(
                name,
                CrudFilterRule.enumValues(name, allowedValues, false),
                ENUM_OPERATORS,
                List.of());
    }

    /** 允许 NULL 判断的枚举或固定字符串字段。 */
    public static <E> CrudFilterField<E> nullableEnumValues(
            String name, Set<String> allowedValues) {
        return new CrudFilterField<>(
                name,
                CrudFilterRule.enumValues(name, allowedValues, true),
                NULLABLE_ENUM_OPERATORS,
                List.of());
    }

    /** 使用声明的相对时间变量和 ISO 字面量的 LocalDateTime 字段。 */
    public static <E> CrudFilterField<E> localDateTime(
            String name, DateTimeFilterVariable... variables) {
        var declared = List.of(variables);
        var allowed = Set.copyOf(declared);
        if (allowed.size() != declared.size()) {
            throw new IllegalArgumentException("日期筛选变量不能重复");
        }
        return new CrudFilterField<>(
                name,
                CrudFilterRule.localDateTime(name, allowed),
                LOCAL_DATE_TIME_OPERATORS,
                declared.stream().map(DateTimeFilterVariable::token).toList());
    }

    CrudFilterFieldMeta toMeta() {
        return new CrudFilterFieldMeta(
                name, operators.stream().map(CrudFilterOperator::toMeta).toList(), variables);
    }
}
