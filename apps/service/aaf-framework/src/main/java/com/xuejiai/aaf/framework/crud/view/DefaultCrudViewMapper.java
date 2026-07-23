package com.xuejiai.aaf.framework.crud.view;

import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.util.JsonUtils;

/** 通用 Entity 到 VO 视图组装器，不触发跨资源 JPA 导航。 */
@Component("defaultCrudViewMapper")
public final class DefaultCrudViewMapper implements CrudViewMapper<BaseEntity, Object> {

    @Override
    @SuppressWarnings("unchecked")
    public Object toView(
            BaseEntity entity, Class<Object> viewType, CrudViewPlan plan, CrudViewData viewData) {
        var entityValues =
                (java.util.Map<String, Object>) JsonUtils.convertValue(entity, LinkedHashMap.class);
        var output = new LinkedHashMap<String, Object>();
        plan.outputFields().forEach(
                field -> {
                    if (entityValues.containsKey(field)) {
                        output.put(field, entityValues.get(field));
                    }
                });
        plan.dependencies()
                .forEach(
                        (viewField, keys) -> {
                            if (!plan.outputFields().contains(viewField)) {
                                return;
                            }
                            var value =
                                    keys.stream()
                                            .findFirst()
                                            .flatMap(key -> viewData.find(key, entity.getId()))
                                            .orElse(null);
                            output.put(viewField, value);
                        });
        return JsonUtils.convertValue(output, viewType);
    }
}
