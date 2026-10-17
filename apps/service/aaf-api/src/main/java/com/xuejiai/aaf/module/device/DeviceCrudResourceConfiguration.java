package com.xuejiai.aaf.module.device;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitionProvider;
import com.xuejiai.aaf.framework.crud.resource.CrudResourceDefinitions;
import com.xuejiai.aaf.module.device.controller.DeviceController;

/** 设备代码 CRUD 资源注册。 */
@Configuration(proxyBeanMethods = false)
public class DeviceCrudResourceConfiguration {

    @Bean
    CrudResourceDefinitionProvider<?> deviceResourceProvider() {
        return CrudResourceDefinitions.bindCrud(DeviceResource.DEFINITION, DeviceController.class);
    }
}
