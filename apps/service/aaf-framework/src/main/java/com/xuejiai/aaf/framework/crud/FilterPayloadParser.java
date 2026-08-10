package com.xuejiai.aaf.framework.crud;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.filter.CrudFilter;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterOperator;

import tools.jackson.databind.JsonNode;

/** 解析 Base64URL 查询参数。 */
final class FilterPayloadParser {

    private static final String FILTER_PARAM = "filter";
    private static final String FIELD_PATTERN = "[a-zA-Z][a-zA-Z0-9]*";

    private FilterPayloadParser() {}

    static List<CrudFilter> parse(String filter) {
        if (filter == null || filter.isBlank()) {
            return List.of();
        }

        var root = decodePayload(filter);
        if (!root.isObject()
                || !"and".equals(root.path("logic").asString())
                || !root.path("conditions").isArray()) {
            throw invalidFilter();
        }

        var filters = new ArrayList<CrudFilter>();
        for (var condition : root.path("conditions")) {
            filters.add(parseCondition(condition));
        }
        return List.copyOf(filters);
    }

    private static JsonNode decodePayload(String filter) {
        try {
            var json = new String(Base64.getUrlDecoder().decode(filter), StandardCharsets.UTF_8);
            return JsonUtils.readTree(json);
        } catch (RuntimeException exception) {
            throw invalidFilter();
        }
    }

    private static CrudFilter parseCondition(JsonNode condition) {
        if (!condition.isObject()) {
            throw invalidFilter();
        }

        var field = condition.path("field");
        var operator = condition.path("operator");
        var values = condition.path("values");
        if (!field.isString()
                || !field.asString().matches(FIELD_PATTERN)
                || !operator.isString()
                || !values.isArray()) {
            throw invalidFilter();
        }

        var parsedValues = new ArrayList<String>();
        for (var value : values) {
            if (!value.isString() || value.asString().isBlank()) {
                throw invalidFilter();
            }
            parsedValues.add(value.asString());
        }
        var parsedOperator = CrudFilterOperator.fromValue(operator.asString());
        if (!parsedOperator.supportsValueCount(parsedValues.size())) {
            throw invalidFilter();
        }
        return new CrudFilter(field.asString(), parsedOperator, parsedValues);
    }

    private static RuntimeException invalidFilter() {
        return exception(GlobalErrorCode.CRUD_FILTER_FORMAT_INVALID, FILTER_PARAM);
    }
}
