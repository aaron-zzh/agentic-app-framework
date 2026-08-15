package com.xuejiai.aaf.module.system.file.resource;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.system.file.FileConfigController;

/** 文件存储配置 CRUD 资源注册。 */
@Configuration(proxyBeanMethods = false)
public class FileConfigCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> fileConfigResourceProvider() {
        return bindCrud(FileConfigResource.DEFINITION, FileConfigController.class);
    }
}
