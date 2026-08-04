package com.xuejiai.aaf.module.ai.aigc.work.resource;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.ai.aigc.work.controller.AigcWorkController;

@Configuration(proxyBeanMethods = false)
public class AigcWorkCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> aigcWorkResourceProvider() {
        return bindCrud(AigcWorkResource.DEFINITION, AigcWorkController.class);
    }
}
