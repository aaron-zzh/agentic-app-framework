package com.xuejiai.aaf.module.ai.aigc.media;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.ai.aigc.media.controller.AssetController;
import com.xuejiai.aaf.module.ai.aigc.media.controller.MediaController;

/** AIGC Media/Asset 显式资源契约绑定。 */
@Configuration(proxyBeanMethods = false)
public class MediaCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> mediaResourceProvider() {
        return bindCrud(MediaResource.DEFINITION, MediaController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> assetResourceProvider() {
        return bindCrud(AssetResource.DEFINITION, AssetController.class);
    }
}
