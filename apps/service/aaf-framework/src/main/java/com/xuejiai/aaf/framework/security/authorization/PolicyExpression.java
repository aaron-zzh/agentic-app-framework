package com.xuejiai.aaf.framework.security.authorization;

import java.util.List;

/** 安全 JSON DSL 编译后的封闭 AST。 */
public sealed interface PolicyExpression
        permits PolicyExpression.Constant,
                PolicyExpression.AllOf,
                PolicyExpression.AnyOf,
                PolicyExpression.Not,
                PolicyExpression.Predicate {

    record Constant(boolean value) implements PolicyExpression {}

    record AllOf(List<PolicyExpression> expressions) implements PolicyExpression {
        public AllOf {
            expressions = List.copyOf(expressions);
        }
    }

    record AnyOf(List<PolicyExpression> expressions) implements PolicyExpression {
        public AnyOf {
            expressions = List.copyOf(expressions);
        }
    }

    record Not(PolicyExpression expression) implements PolicyExpression {}

    record Predicate(
            String fact, PolicyFactSchema.ValueType factType, Operator operator, Object expected)
            implements PolicyExpression {}

    enum Operator {
        EQ,
        NE,
        IN,
        NOT_IN,
        GT,
        GTE,
        LT,
        LTE,
        CONTAINS,
        EXISTS
    }
}
