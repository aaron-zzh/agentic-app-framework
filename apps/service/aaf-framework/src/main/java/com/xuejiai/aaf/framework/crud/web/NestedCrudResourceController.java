package com.xuejiai.aaf.framework.crud.web;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudService;

/** 非标准路径但复用 BaseCrud 安全内核的嵌套资源端点。 */
public interface NestedCrudResourceController<E extends BaseEntity, V, C, U, P extends PageParam> {

    BaseCrudService<E, V, C, U, P> getService();
}
