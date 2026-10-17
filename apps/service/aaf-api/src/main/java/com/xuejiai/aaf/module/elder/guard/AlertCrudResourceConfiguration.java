package com.xuejiai.aaf.module.elder.guard;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions;
import com.xuejiai.aaf.module.elder.guard.controller.AlertEventController;
import com.xuejiai.aaf.module.elder.guard.controller.AlertHandlingController;

/** 预警代码 CRUD 资源注册。 */
@Configuration(proxyBeanMethods = false)
public class AlertCrudResourceConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> alertEventResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                AlertEventResource.DEFINITION, AlertEventController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> alertHandlingResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                AlertHandlingResource.DEFINITION, AlertHandlingController.class);
    }
}
