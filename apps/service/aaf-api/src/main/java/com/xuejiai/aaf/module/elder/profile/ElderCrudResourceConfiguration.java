package com.xuejiai.aaf.module.elder.profile;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions;
import com.xuejiai.aaf.module.elder.profile.controller.ElderContactController;
import com.xuejiai.aaf.module.elder.profile.controller.ElderController;
import com.xuejiai.aaf.module.elder.profile.controller.ElderHealthItemController;
import com.xuejiai.aaf.module.elder.profile.controller.MedicationPlanController;
import com.xuejiai.aaf.module.elder.profile.controller.MedicationRecordController;

/** 长者中心代码 CRUD 资源注册。 */
@Configuration(proxyBeanMethods = false)
public class ElderCrudResourceConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> elderResourceProvider() {
        return CrudResourceDefinitions.bindCrud(ElderResource.DEFINITION, ElderController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> elderContactResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                ElderContactResource.DEFINITION, ElderContactController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> elderHealthItemResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                ElderHealthItemResource.DEFINITION, ElderHealthItemController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> medicationPlanResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                MedicationPlanResource.DEFINITION, MedicationPlanController.class);
    }

    @Bean
    CrudResourceDefinitionProvider<?> medicationRecordResourceProvider() {
        return CrudResourceDefinitions.bindCrud(
                MedicationRecordResource.DEFINITION, MedicationRecordController.class);
    }
}
