package com.xuejiai.aaf.framework.crud.filter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrudFilterSupportTest {

    @Test
    @DisplayName("Given 未声明字段 When 构建筛选 Then 拒绝该字段")
    void should_reject_undeclared_field() {
        var filter = new CrudFilter("internalField", CrudFilterOperator.EQ, List.of("value"));

        assertThatThrownBy(
                        () ->
                                CrudFilterSupport.build(
                                        List.of(filter),
                                        Map.<String, CrudFilterRule<Object>>of(),
                                        evaluationContext()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Given 非法枚举值 When 构建筛选 Then 使用通用请求错误拒绝")
    void should_reject_invalid_enum_value() {
        var filter = new CrudFilter("status", CrudFilterOperator.EQ, List.of("closed"));
        var rules =
                Map.of(
                        "status",
                        CrudFilterRule.enumValues("status", java.util.Set.of("open"), false));

        assertThatThrownBy(
                        () -> CrudFilterSupport.build(List.of(filter), rules, evaluationContext()))
                .isInstanceOf(RuntimeException.class);
    }

    private FilterEvaluationContext evaluationContext() {
        return FilterEvaluationContext.create(
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
