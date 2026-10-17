package com.xuejiai.aaf.module.elder.album;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions;
import com.xuejiai.aaf.module.elder.album.controller.ElderPhotoController;

/** 养老相册代码 CRUD 资源注册。 */
@Configuration(proxyBeanMethods = false)
public class AlbumCrudResourceConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> elderPhotoResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                ElderPhotoResource.DEFINITION, ElderPhotoController.class);
    }
}
