package com.xuejiai.aaf.framework.crud.filter;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 资源字段可用的筛选操作符。 */
@Schema(description = "资源字段筛选能力")
public record CrudFilterFieldMeta(
        @Schema(description = "资源字段名") String field,
        @Schema(description = "该字段允许的筛选操作符") List<CrudFilterOperatorMeta> operators,
        @Schema(description = "该字段允许使用的内置变量") List<String> variables) {
    public CrudFilterFieldMeta {
        operators = List.copyOf(operators);
        variables = List.copyOf(variables);
    }

    public CrudFilterFieldMeta(String field, List<CrudFilterOperatorMeta> operators) {
        this(field, operators, List.of());
    }
}
