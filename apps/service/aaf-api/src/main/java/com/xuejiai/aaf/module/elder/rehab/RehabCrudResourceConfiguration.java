package com.xuejiai.aaf.module.elder.rehab;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions;
import com.xuejiai.aaf.module.elder.rehab.controller.RehabGuideController;

/** 康复指导代码 CRUD 资源注册。 */
@Configuration(proxyBeanMethods = false)
public class RehabCrudResourceConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> rehabGuideResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                RehabGuideResource.DEFINITION, RehabGuideController.class);
    }
}
