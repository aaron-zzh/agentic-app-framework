package com.xuejiai.aaf.module.billing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.filter.CrudFilter;
import com.xuejiai.aaf.framework.crud.filter.CrudFilterOperator;

/** Billing 资源的显式等值筛选支持。 */
final class BillingCrudFilterSupport {

    private BillingCrudFilterSupport() {}

    static <E> Specification<E> equalsOrIn(
            List<CrudFilter> filters, Set<String> textFields, Set<String> numberFields) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            for (var filter : filters) {
                if (!textFields.contains(filter.field())
                        && !numberFields.contains(filter.field())) {
                    throw unsupported(filter);
                }
                if (filter.values().isEmpty()
                        || (filter.operator() != CrudFilterOperator.EQ
                                && filter.operator() != CrudFilterOperator.IN)) {
                    throw unsupported(filter);
                }
                var values =
                        numberFields.contains(filter.field())
                                ? filter.values().stream()
                                        .map(BillingCrudFilterSupport::parseLong)
                                        .toList()
                                : filter.values();
                var path = root.get(filter.field());
                predicates.add(
                        filter.operator() == CrudFilterOperator.EQ
                                ? cb.equal(path, values.getFirst())
                                : path.in(values));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "筛选值必须为数字: " + value);
        }
    }

    private static BusinessException unsupported(CrudFilter filter) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, "不支持的筛选条件: " + filter.field());
    }
}
