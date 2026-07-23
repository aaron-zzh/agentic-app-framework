package com.xuejiai.aaf.framework.crud.resource;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceDefinition;

/** 业务模块提供资源契约及其受信任 HTTP 端点绑定。 */
public interface CrudResourceDefinitionProvider<E extends BaseEntity> {

    CrudResourceDefinition<E> definition();

    CrudResourceEndpointBinding endpointBinding();
}
