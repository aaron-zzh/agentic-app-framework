package com.xuejiai.aaf.framework.crud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.framework.crud.filter.CrudFilter;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterOperator;

class FilterPayloadParserTest {

    @Test
    @DisplayName("Given Base64URL 筛选负载 When 解析 Then 返回数组值条件")
    void should_parse_base64_url_payload() {
        var filter =
                encode(
                        """
                {"logic":"and","conditions":[
                  {"field":"dueDate","operator":"between","values":["$now","$nowPlus3Days"]},
                  {"field":"status","operator":"eq","values":["pending"]}
                ]}
                """);

        Assertions.assertThat(FilterPayloadParser.parse(filter))
                .containsExactly(
                        new CrudFilter(
                                "dueDate",
                                CrudFilterOperator.BETWEEN,
                                java.util.List.of("$now", "$nowPlus3Days")),
                        new CrudFilter(
                                "status", CrudFilterOperator.EQ, java.util.List.of("pending")));
    }

    @Test
    @DisplayName("Given 无值操作符负载 When 解析 Then 接受空值数组")
    void should_parse_zero_value_operator() {
        var filter =
                encode(
                        "{\"logic\":\"and\",\"conditions\":[{\"field\":\"dueDate\",\"operator\":\"isNull\",\"values\":[]}]}");

        assertThat(FilterPayloadParser.parse(filter))
                .containsExactly(
                        new CrudFilter("dueDate", CrudFilterOperator.IS_NULL, java.util.List.of()));
    }

    @Test
    @DisplayName("Given 操作符参数数量错误 When 解析 Then 拒绝筛选格式")
    void should_reject_invalid_operator_arity() {
        var filter =
                encode(
                        "{\"logic\":\"and\",\"conditions\":[{\"field\":\"status\",\"operator\":\"eq\",\"values\":[]}]}");

        assertThatThrownBy(() -> FilterPayloadParser.parse(filter))
                .hasMessageContaining("筛选条件格式非法");
    }

    @Test
    @DisplayName("Given 非法负载结构 When 解析 Then 拒绝筛选格式")
    void should_reject_invalid_payload() {
        assertThatThrownBy(() -> FilterPayloadParser.parse(encode("{" + "\"logic\":\"or\"}")))
                .hasMessageContaining("筛选条件格式非法");
    }

    @Test
    @DisplayName("Given 未支持操作符 When 解析 Then 保留操作符错误")
    void should_reject_unsupported_operator() {
        var filter =
                encode(
                        "{\"logic\":\"and\",\"conditions\":[{\"field\":\"status\",\"operator\":\"matches\",\"values\":[\"pending\"]}]}");

        assertThatThrownBy(() -> FilterPayloadParser.parse(filter))
                .hasMessageContaining("不支持的筛选操作符");
    }

    private String encode(String payload) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
