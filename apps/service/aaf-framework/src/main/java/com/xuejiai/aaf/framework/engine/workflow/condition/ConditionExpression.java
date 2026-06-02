package com.xuejiai.aaf.framework.engine.workflow.condition;

/**
 * 条件表达式——单个比较条件。
 *
 * @param field 表单字段名
 * @param operator 比较运算符
 * @param value 比较值
 * @param logic 与下一个条件的逻辑关系（AND/OR）
 */
public record ConditionExpression(String field, Operator operator, Object value, Logic logic) {

    /** 比较运算符 */
    public enum Operator {
        EQ,
        NEQ,
        GT,
        GTE,
        LT,
        LTE,
        IN,
        CONTAINS
    }

    /** 逻辑关系 */
    public enum Logic {
        AND,
        OR
    }
}
