package com.xuejiai.aaf.module.ai.aigc.execution.resource;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.ai.aigc.execution.controller.AigcExecutionBindingController;
import com.xuejiai.aaf.module.ai.aigc.execution.controller.AigcExecutionRunController;

@Configuration(proxyBeanMethods = false)
public class AigcExecutionCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> aigcExecutionRunResourceProvider() {
        return bindCrud(AigcExecutionRunResource.DEFINITION, AigcExecutionRunController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcExecutionBindingResourceProvider() {
        return bindCrud(
                AigcExecutionBindingResource.DEFINITION, AigcExecutionBindingController.class);
    }
}
