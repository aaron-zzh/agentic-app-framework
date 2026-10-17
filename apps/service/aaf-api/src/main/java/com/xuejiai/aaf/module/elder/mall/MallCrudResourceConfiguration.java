package com.xuejiai.aaf.module.elder.mall;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions;
import com.xuejiai.aaf.module.elder.mall.controller.ServiceItemController;
import com.xuejiai.aaf.module.elder.mall.controller.ServiceOrderController;

/** 服务商城代码 CRUD 资源注册。 */
@Configuration(proxyBeanMethods = false)
public class MallCrudResourceConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> serviceItemResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                ServiceItemResource.DEFINITION, ServiceItemController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> serviceOrderResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                ServiceOrderResource.DEFINITION, ServiceOrderController.class);
    }
}
