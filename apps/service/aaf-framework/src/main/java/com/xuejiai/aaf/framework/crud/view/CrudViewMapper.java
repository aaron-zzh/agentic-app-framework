package com.xuejiai.aaf.framework.crud.view;

/** 根据字段集、字段权限和预加载数据将 Entity 转为接口输出视图。 */
public interface CrudViewMapper<E, V> {

    V toView(E entity, Class<V> viewType, CrudViewPlan plan, CrudViewData viewData);
}
