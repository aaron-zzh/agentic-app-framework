package com.xuejiai.aaf.module.ai.aigc.configuration.resource;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcChannelSpecController;
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcDomainExtensionController;
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcProjectBlueprintController;
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcProjectTypeController;
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcProjectTypePackageController;
import com.xuejiai.aaf.module.ai.aigc.configuration.controller.AigcSnippetController;

/** AIGC configuration 显式资源契约绑定。 */
@Configuration(proxyBeanMethods = false)
public class AigcConfigurationCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> aigcProjectTypeResourceProvider() {
        return bindCrud(AigcProjectTypeResource.DEFINITION, AigcProjectTypeController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcProjectTypePackageResourceProvider() {
        return bindCrud(
                AigcProjectTypePackageResource.DEFINITION, AigcProjectTypePackageController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcProjectBlueprintResourceProvider() {
        return bindCrud(
                AigcProjectBlueprintResource.DEFINITION, AigcProjectBlueprintController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcDomainExtensionResourceProvider() {
        return bindCrud(
                AigcDomainExtensionResource.DEFINITION, AigcDomainExtensionController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcChannelSpecResourceProvider() {
        return bindCrud(AigcChannelSpecResource.DEFINITION, AigcChannelSpecController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcSnippetResourceProvider() {
        return bindCrud(AigcSnippetResource.DEFINITION, AigcSnippetController.class);
    }
}
