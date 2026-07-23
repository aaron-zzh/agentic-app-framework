package com.xuejiai.aaf.framework.crud.definition;

import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.framework.crud.filter.CrudFilterSchema;

/** 资源查询能力上限：筛选、显式排序与默认排序。 */
public record CrudQueryDefinition<E>(
        CrudFilterSchema<E> filterSchema, Set<String> sortableFields, Sort defaultSort) {

    public CrudQueryDefinition {
        filterSchema = Objects.requireNonNull(filterSchema, "filterSchema");
        sortableFields = Set.copyOf(sortableFields);
        defaultSort = Objects.requireNonNull(defaultSort, "defaultSort");
    }
}
