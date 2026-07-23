package com.xuejiai.aaf.framework.crud.filter;

import java.util.List;

/** 查询窗口的单个筛选条件。 */
public record CrudFilter(String field, CrudFilterOperator operator, List<String> values) {
    public CrudFilter {
        values = List.copyOf(values);
    }
}
