package com.xuejiai.aaf.framework.crud.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.enums.ArrayValuable;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceTypeContract;
import com.xuejiai.aaf.framework.crud.definition.CrudViewDefinition;

import jakarta.persistence.Column;

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

    @Test
    @DisplayName("Given 类型化查询字段与公开列表字段 When 推导默认筛选 Then 仅开放安全交集")
    void should_infer_safe_defaults_from_page_list_and_entity_intersection() {
        var types =
                new CrudResourceTypeContract<>(
                        FilterEntity.class, Void.class, Void.class, Object.class, FilterPage.class);
        var view =
                new CrudViewDefinition(
                        Map.of("list", Set.of("status", "category", "title", "internalId")), "");

        var metas = CrudFilterSchema.safeDefaults(types, view).metas();

        assertThat(metas)
                .extracting(CrudFilterFieldMeta::field)
                .containsExactly("status", "category", "title");
        assertThat(metas.get(0).operators()).doesNotContain(CrudFilterOperator.IS_NULL.toMeta());
        assertThat(metas.get(1).operators()).contains(CrudFilterOperator.IS_NULL.toMeta());
    }

    private enum FilterStatus implements ArrayValuable<String> {
        VALUES;

        @Override
        public String[] array() {
            return new String[] {"pending", "done"};
        }
    }

    private static final class FilterEntity extends BaseEntity {
        @Column(nullable = false)
        private String status;

        private String category;
        private String title;
        private Long internalId;
    }

    private static final class FilterPage extends PageParam {
        @InEnum(FilterStatus.class)
        private String status;

        @InEnum(FilterStatus.class)
        private String category;

        private String title;
        private Long internalId;
        private String hidden;
    }
}
