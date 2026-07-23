package com.xuejiai.aaf.framework.crud.filter;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;

/** 为资源声明的筛选规则生成 JPA Specification。 */
public final class CrudFilterSupport {

    private CrudFilterSupport() {}

    /**
     * 按规则表应用筛选条件。
     *
     * <p>未在 {@code rules} 中显式注册的字段一律拒绝，避免客户端借筛选接口访问实体内部字段。
     */
    public static <E> Specification<E> build(
            List<CrudFilter> filters,
            Map<String, CrudFilterRule<E>> rules,
            FilterEvaluationContext context) {
        Objects.requireNonNull(filters, "filters");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(context, "context");

        var builder = SpecificationBuilder.<E>builder();
        for (var filter : filters) {
            var rule = rules.get(filter.field());
            if (rule == null) {
                throw exception(GlobalErrorCode.CRUD_FILTER_UNSUPPORTED);
            }
            rule.apply(builder, filter, context);
        }
        return builder.build();
    }
}
