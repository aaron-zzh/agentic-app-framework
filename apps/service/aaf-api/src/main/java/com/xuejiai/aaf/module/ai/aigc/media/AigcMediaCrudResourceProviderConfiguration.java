package com.xuejiai.aaf.module.ai.aigc.media;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.ai.aigc.media.controller.AigcAssetCategoryController;
import com.xuejiai.aaf.module.ai.aigc.media.controller.AigcAssetCollectionController;
import com.xuejiai.aaf.module.ai.aigc.media.controller.AigcAssetController;
import com.xuejiai.aaf.module.ai.aigc.media.controller.AigcAssetTagController;
import com.xuejiai.aaf.module.ai.aigc.media.controller.AigcMediaController;

/** AIGC Media/Asset 显式资源契约绑定。 */
@Configuration(proxyBeanMethods = false)
public class AigcMediaCrudResourceProviderConfiguration {
    @Bean
    CrudResourceDefinitionProvider<?> aigcMediaResourceProvider() {
        return bindCrud(AigcMediaResource.DEFINITION, AigcMediaController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcAssetResourceProvider() {
        return bindCrud(AigcAssetResource.DEFINITION, AigcAssetController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcAssetCategoryResourceProvider() {
        return bindCrud(AigcAssetCategoryResource.DEFINITION, AigcAssetCategoryController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcAssetTagResourceProvider() {
        return bindCrud(AigcAssetTagResource.DEFINITION, AigcAssetTagController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> aigcAssetCollectionResourceProvider() {
        return bindCrud(
                AigcAssetCollectionResource.DEFINITION, AigcAssetCollectionController.class);
    }
}
