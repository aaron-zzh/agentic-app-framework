package com.xuejiai.aaf.framework.crud.filter;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;

/**
 * 资源显式声明的单字段筛选规则。
 *
 * <p>规则只负责操作符和值的校验及 Specification 条件构造；字段白名单仍由各资源 Service 注册，不能从客户端请求或 EntityDef 推导。
 */
@FunctionalInterface
public interface CrudFilterRule<E> {

    void apply(SpecificationBuilder<E> builder, CrudFilter filter, FilterEvaluationContext context);

    /** 文本字段：支持等于、包含、前缀及空字符串判断。 */
    static <E> CrudFilterRule<E> text(String property) {
        return (builder, filter, context) -> {
            switch (filter.operator()) {
                case EQ -> builder.eqIfPresent(property, singleValue(filter));
                case CONTAINS -> builder.likeIfPresent(property, singleValue(filter));
                case STARTS_WITH -> builder.startsWithIfPresent(property, singleValue(filter));
                case IS_EMPTY -> {
                    requireNoValues(filter);
                    builder.isEmpty(property);
                }
                case IS_NOT_EMPTY -> {
                    requireNoValues(filter);
                    builder.isNotEmpty(property);
                }
                default -> throw invalidFilterValue();
            }
        };
    }

    /** 枚举或固定字符串字段：支持等于、集合包含，可按需允许 NULL 判断。 */
    static <E> CrudFilterRule<E> enumValues(
            String property, Set<String> allowedValues, boolean supportsNullChecks) {
        var allowed = Set.copyOf(allowedValues);
        return (builder, filter, context) -> {
            var values = filter.values();
            if (!values.isEmpty() && !allowed.containsAll(values)) {
                throw invalidFilterValue();
            }

            switch (filter.operator()) {
                case EQ -> builder.eqIfPresent(property, singleValue(filter));
                case IN -> builder.inIfPresent(property, nonEmptyValues(filter));
                case NOT_IN -> builder.notInIfPresent(property, nonEmptyValues(filter));
                case IS_NULL -> {
                    if (!supportsNullChecks) {
                        throw invalidFilterValue();
                    }
                    requireNoValues(filter);
                    builder.isNull(property);
                }
                case IS_NOT_NULL -> {
                    if (!supportsNullChecks) {
                        throw invalidFilterValue();
                    }
                    requireNoValues(filter);
                    builder.isNotNull(property);
                }
                default -> throw invalidFilterValue();
            }
        };
    }

    /** LocalDateTime 字段：支持区间、比较、NULL 判断及字段级相对时间变量白名单。 */
    static <E> CrudFilterRule<E> localDateTime(
            String property, Set<DateTimeFilterVariable> allowedVariables) {
        var allowed = Set.copyOf(allowedVariables);
        return (builder, filter, context) -> {
            var values = filter.values();
            switch (filter.operator()) {
                case IS_NULL -> {
                    requireNoValues(filter);
                    builder.isNull(property);
                }
                case IS_NOT_NULL -> {
                    requireNoValues(filter);
                    builder.isNotNull(property);
                }
                case BETWEEN -> {
                    if (values.size() != 2) {
                        throw invalidFilterValue();
                    }
                    var start =
                            DateTimeFilterValueResolver.resolve(
                                    values.getFirst(), context, allowed);
                    var end = DateTimeFilterValueResolver.resolve(values.get(1), context, allowed);
                    builder.geIfPresent(property, start).ltIfPresent(property, end);
                }
                case GT, GTE, LT, LTE -> {
                    var value =
                            DateTimeFilterValueResolver.resolve(
                                    singleValue(filter), context, allowed);
                    switch (filter.operator()) {
                        case GT -> builder.gtIfPresent(property, value);
                        case GTE -> builder.geIfPresent(property, value);
                        case LT -> builder.ltIfPresent(property, value);
                        case LTE -> builder.leIfPresent(property, value);
                        default -> throw invalidFilterValue();
                    }
                }
                default -> throw invalidFilterValue();
            }
        };
    }

    private static String singleValue(CrudFilter filter) {
        if (filter.values().size() != 1) {
            throw invalidFilterValue();
        }
        return filter.values().getFirst();
    }

    private static List<String> nonEmptyValues(CrudFilter filter) {
        if (filter.values().isEmpty()) {
            throw invalidFilterValue();
        }
        return filter.values();
    }

    private static void requireNoValues(CrudFilter filter) {
        if (!filter.values().isEmpty()) {
            throw invalidFilterValue();
        }
    }

    private static RuntimeException invalidFilterValue() {
        return exception(GlobalErrorCode.BAD_REQUEST);
    }
}
