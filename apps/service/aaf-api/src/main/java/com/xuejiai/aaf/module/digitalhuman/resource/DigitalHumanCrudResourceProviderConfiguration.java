package com.xuejiai.aaf.module.digitalhuman.resource;

import static com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions.bindCrud;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.module.digitalhuman.controller.CreditConsumptionController;
import com.xuejiai.aaf.module.digitalhuman.controller.DigitalHumanController;
import com.xuejiai.aaf.module.digitalhuman.controller.PresetVoiceController;

/** 数字人模块显式资源契约绑定。 */
@Configuration(proxyBeanMethods = false)
public class DigitalHumanCrudResourceProviderConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> digitalHumanResourceProvider() {
        return bindCrud(DigitalHumanResource.DEFINITION, DigitalHumanController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> digitalHumanPresetVoiceResourceProvider() {
        return bindCrud(PresetVoiceResource.DEFINITION, PresetVoiceController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> digitalHumanCreditConsumptionResourceProvider() {
        return bindCrud(CreditConsumptionResource.DEFINITION, CreditConsumptionController.class);
    }
}
