package com.xuejiai.aaf.framework.crud.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;

/** 资源声明的高级筛选字段白名单。 */
public final class CrudFilterSchema<E> {

    private final List<CrudFilterField<E>> fields;
    private final List<CrudFilterFieldMeta> metas;
    private final Map<String, CrudFilterRule<E>> rules;

    private CrudFilterSchema(List<CrudFilterField<E>> fields) {
        this.fields = List.copyOf(fields);

        var mutableRules = new LinkedHashMap<String, CrudFilterRule<E>>();
        var mutableMetas = new ArrayList<CrudFilterFieldMeta>(fields.size());
        for (var field : this.fields) {
            if (mutableRules.putIfAbsent(field.name(), field.rule()) != null) {
                throw new IllegalArgumentException("筛选字段重复: " + field.name());
            }
            mutableMetas.add(field.toMeta());
        }
        this.rules = Map.copyOf(mutableRules);
        this.metas = List.copyOf(mutableMetas);
    }

    @SafeVarargs
    public static <E> CrudFilterSchema<E> of(CrudFilterField<E>... fields) {
        return new CrudFilterSchema<>(List.of(fields));
    }

    public static <E> CrudFilterSchema<E> empty() {
        return new CrudFilterSchema<>(List.of());
    }

    /** 按声明顺序返回供客户端渲染的筛选字段元数据。 */
    public List<CrudFilterFieldMeta> metas() {
        return metas;
    }

    /** 使用同一份白名单和规则构建服务端查询条件。 */
    public Specification<E> build(List<CrudFilter> filters, FilterEvaluationContext context) {
        return CrudFilterSupport.build(filters, rules, context);
    }
}
