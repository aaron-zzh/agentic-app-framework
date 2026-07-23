package com.xuejiai.aaf.framework.crud.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrudFilterSchemaTest {

    @Test
    @DisplayName("Given 类型化筛选字段 When 创建 Schema Then 从同一声明生成有序客户端元数据")
    void should_generate_filter_metadata_from_typed_fields() {
        var schema =
                CrudFilterSchema.<Object>of(
                        CrudFilterField.text("title"),
                        CrudFilterField.enumValues("status", Set.of("pending")),
                        CrudFilterField.nullableEnumValues("category", Set.of("todo")),
                        CrudFilterField.localDateTime(
                                "dueDate",
                                DateTimeFilterVariable.NOW,
                                DateTimeFilterVariable.TODAY_START));

        assertThat(schema.metas())
                .containsExactly(
                        new CrudFilterFieldMeta(
                                "title",
                                List.of(
                                        CrudFilterOperator.EQ.toMeta(),
                                        CrudFilterOperator.CONTAINS.toMeta(),
                                        CrudFilterOperator.STARTS_WITH.toMeta(),
                                        CrudFilterOperator.IS_EMPTY.toMeta(),
                                        CrudFilterOperator.IS_NOT_EMPTY.toMeta())),
                        new CrudFilterFieldMeta(
                                "status",
                                List.of(
                                        CrudFilterOperator.EQ.toMeta(),
                                        CrudFilterOperator.IN.toMeta(),
                                        CrudFilterOperator.NOT_IN.toMeta())),
                        new CrudFilterFieldMeta(
                                "category",
                                List.of(
                                        CrudFilterOperator.EQ.toMeta(),
                                        CrudFilterOperator.IN.toMeta(),
                                        CrudFilterOperator.NOT_IN.toMeta(),
                                        CrudFilterOperator.IS_NULL.toMeta(),
                                        CrudFilterOperator.IS_NOT_NULL.toMeta())),
                        new CrudFilterFieldMeta(
                                "dueDate",
                                List.of(
                                        CrudFilterOperator.BETWEEN.toMeta(),
                                        CrudFilterOperator.GT.toMeta(),
                                        CrudFilterOperator.GTE.toMeta(),
                                        CrudFilterOperator.LT.toMeta(),
                                        CrudFilterOperator.LTE.toMeta(),
                                        CrudFilterOperator.IS_NULL.toMeta(),
                                        CrudFilterOperator.IS_NOT_NULL.toMeta()),
                                List.of("$now", "$todayStart")));
    }

    @Test
    @DisplayName("Given Schema 声明字段 When 构建筛选 Then 使用同一字段规则")
    void should_build_filter_from_declared_field_rule() {
        var schema = CrudFilterSchema.<Object>of(CrudFilterField.text("title"));

        assertThat(
                        schema.build(
                                List.of(
                                        new CrudFilter(
                                                "title",
                                                CrudFilterOperator.CONTAINS,
                                                List.of("需求"))),
                                FilterEvaluationContext.create(
                                        Clock.fixed(
                                                Instant.parse("2026-07-19T00:00:00Z"),
                                                ZoneOffset.UTC),
                                        ZoneOffset.UTC)))
                .isNotNull();
    }

    @Test
    @DisplayName("Given 重复筛选字段 When 创建 Schema Then 拒绝重复白名单声明")
    void should_reject_duplicate_filter_field() {
        assertThatThrownBy(
                        () ->
                                CrudFilterSchema.<Object>of(
                                        CrudFilterField.text("title"),
                                        CrudFilterField.text("title")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("筛选字段重复");
    }
}
