package com.xuejiai.aaf.framework.crud.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrudFilterOperatorTest {

    @Test
    @DisplayName("Given 支持的操作符 When 解析 Then 返回对应枚举")
    void should_parse_supported_operator() {
        assertThat(CrudFilterOperator.fromValue("eq")).isEqualTo(CrudFilterOperator.EQ);
        assertThat(CrudFilterOperator.fromValue("notIn")).isEqualTo(CrudFilterOperator.NOT_IN);
        assertThat(CrudFilterOperator.fromValue("contains")).isEqualTo(CrudFilterOperator.CONTAINS);
        assertThat(CrudFilterOperator.fromValue("gte")).isEqualTo(CrudFilterOperator.GTE);
        assertThat(CrudFilterOperator.fromValue("between")).isEqualTo(CrudFilterOperator.BETWEEN);
        assertThat(CrudFilterOperator.fromValue("isNull")).isEqualTo(CrudFilterOperator.IS_NULL);
    }

    @Test
    @DisplayName("Given 操作符参数数量 When 校验 Then 按公共契约约束")
    void should_validate_operator_value_counts() {
        assertThat(CrudFilterOperator.IN.supportsValueCount(1)).isTrue();
        assertThat(CrudFilterOperator.IN.supportsValueCount(2)).isTrue();
        assertThat(CrudFilterOperator.BETWEEN.supportsValueCount(2)).isTrue();
        assertThat(CrudFilterOperator.BETWEEN.supportsValueCount(1)).isFalse();
        assertThat(CrudFilterOperator.IS_NULL.supportsValueCount(0)).isTrue();
        assertThat(CrudFilterOperator.IS_NULL.supportsValueCount(1)).isFalse();
        assertThat(CrudFilterOperator.IS_NOT_EMPTY.supportsValueCount(0)).isTrue();
    }

    @Test
    @DisplayName("Given 未支持的操作符 When 解析 Then 拒绝请求")
    void should_reject_unsupported_operator() {
        assertThatThrownBy(() -> CrudFilterOperator.fromValue("matches"))
                .hasMessageContaining("不支持的筛选操作符");
    }
}
