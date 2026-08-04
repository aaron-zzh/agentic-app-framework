package com.xuejiai.aaf.module.content;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.content.controller.ContentBrandProfileController;
import com.xuejiai.aaf.module.content.controller.ContentChannelSpecController;
import com.xuejiai.aaf.module.content.controller.ContentDomainExtensionController;
import com.xuejiai.aaf.module.content.controller.ContentExecutionRunController;
import com.xuejiai.aaf.module.content.controller.ContentObjectVersionController;
import com.xuejiai.aaf.module.content.controller.ContentProjectBlueprintController;
import com.xuejiai.aaf.module.content.controller.ContentProjectController;
import com.xuejiai.aaf.module.content.controller.ContentProjectObjectController;
import com.xuejiai.aaf.module.content.controller.ContentProjectProfileRefController;
import com.xuejiai.aaf.module.content.controller.ContentProjectRelationController;
import com.xuejiai.aaf.module.content.controller.ContentProjectTypeController;
import com.xuejiai.aaf.module.content.controller.ContentSnippetController;

/** Content Studio 显式资源契约与类型化 Controller 的集中绑定。 */
@Configuration(proxyBeanMethods = false)
public class ContentCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> contentBrandProfileResourceProvider() {
        return bindCrud(
                ContentBrandProfileResource.DEFINITION, ContentBrandProfileController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentChannelSpecResourceProvider() {
        return bindCrud(ContentChannelSpecResource.DEFINITION, ContentChannelSpecController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentDomainExtensionResourceProvider() {
        return bindCrud(
                ContentDomainExtensionResource.DEFINITION,
                ContentDomainExtensionController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentExecutionRunResourceProvider() {
        return bindCrud(ContentExecutionRunResource.DEFINITION, ContentExecutionRunController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentObjectVersionResourceProvider() {
        return bindCrud(ContentObjectVersionResource.DEFINITION, ContentObjectVersionController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentProjectBlueprintResourceProvider() {
        return bindCrud(
                ContentProjectBlueprintResource.DEFINITION,
                ContentProjectBlueprintController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentProjectResourceProvider() {
        return bindCrud(ContentProjectResource.DEFINITION, ContentProjectController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentProjectObjectResourceProvider() {
        return bindCrud(
                ContentProjectObjectResource.DEFINITION, ContentProjectObjectController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentProjectProfileRefResourceProvider() {
        return bindCrud(
                ContentProjectProfileRefResource.DEFINITION,
                ContentProjectProfileRefController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentProjectRelationResourceProvider() {
        return bindCrud(
                ContentProjectRelationResource.DEFINITION,
                ContentProjectRelationController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentProjectTypeResourceProvider() {
        return bindCrud(ContentProjectTypeResource.DEFINITION, ContentProjectTypeController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> contentSnippetResourceProvider() {
        return bindCrud(ContentSnippetResource.DEFINITION, ContentSnippetController.class);
    }
}
