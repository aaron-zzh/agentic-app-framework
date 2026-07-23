package com.xuejiai.aaf.framework.crud.filter;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.Arrays;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/** 查询窗口支持的筛选操作符。 */
public enum CrudFilterOperator {
    EQ("eq", 1, 1),
    IN("in", 1, null),
    NOT_IN("notIn", 1, null),
    CONTAINS("contains", 1, 1),
    STARTS_WITH("startsWith", 1, 1),
    GT("gt", 1, 1),
    GTE("gte", 1, 1),
    LT("lt", 1, 1),
    LTE("lte", 1, 1),
    BETWEEN("between", 2, 2),
    IS_NULL("isNull", 0, 0),
    IS_NOT_NULL("isNotNull", 0, 0),
    IS_EMPTY("isEmpty", 0, 0),
    IS_NOT_EMPTY("isNotEmpty", 0, 0);

    private final String value;
    private final int minValues;
    private final Integer maxValues;

    CrudFilterOperator(String value, int minValues, Integer maxValues) {
        this.value = value;
        this.minValues = minValues;
        this.maxValues = maxValues;
    }

    /** 返回供客户端筛选构建器渲染的操作符元数据。 */
    public CrudFilterOperatorMeta toMeta() {
        return new CrudFilterOperatorMeta(value, minValues, maxValues);
    }

    /** 判断值数量是否符合操作符约束。 */
    public boolean supportsValueCount(int count) {
        return count >= minValues && (maxValues == null || count <= maxValues);
    }

    /** 从 URL 操作符值解析枚举。 */
    public static CrudFilterOperator fromValue(String value) {
        return Arrays.stream(values())
                .filter(operator -> operator.value.equals(value))
                .findFirst()
                .orElseThrow(
                        () -> exception(GlobalErrorCode.CRUD_FILTER_OPERATOR_UNSUPPORTED, value));
    }
}
