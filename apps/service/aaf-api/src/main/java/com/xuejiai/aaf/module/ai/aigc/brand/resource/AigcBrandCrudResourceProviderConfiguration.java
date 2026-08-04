package com.xuejiai.aaf.module.ai.aigc.brand.resource;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.ai.aigc.brand.controller.AigcBrandProfileController;

/** AIGC brand 显式资源契约绑定。 */
@Configuration(proxyBeanMethods = false)
public class AigcBrandCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> aigcBrandProfileResourceProvider() {
        return bindCrud(AigcBrandProfileResource.DEFINITION, AigcBrandProfileController.class);
    }
}
