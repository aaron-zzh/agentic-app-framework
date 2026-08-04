package com.xuejiai.aaf.module.ai.aigc.task.resource;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.ai.aigc.task.controller.AigcTaskController;

/** AIGC Task 显式资源契约绑定。 */
@Configuration(proxyBeanMethods = false)
public class AigcTaskCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> aigcTaskResourceProvider() {
        return bindCrud(AigcTaskResource.DEFINITION, AigcTaskController.class);
    }
}
