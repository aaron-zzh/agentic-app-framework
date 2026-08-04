package com.xuejiai.aaf.module.ai.aigc.project.resource;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.ai.aigc.project.controller.AigcProjectController;

@Configuration(proxyBeanMethods = false)
public class AigcProjectCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> aigcProjectResourceProvider() {
        return bindCrud(AigcProjectResource.DEFINITION, AigcProjectController.class);
    }
}
