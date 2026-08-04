package com.xuejiai.aaf.module.ai.aigc.timeline.resource;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.ai.aigc.timeline.controller.AigcTimelineController;

@Configuration(proxyBeanMethods = false)
public class AigcTimelineCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> aigcTimelineResourceProvider() {
        return bindCrud(AigcTimelineResource.DEFINITION, AigcTimelineController.class);
    }
}
