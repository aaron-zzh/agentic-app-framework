package com.xuejiai.aaf.module.system.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.storage.StorageCredentialProvider;

/** 动态文件存储应用层配置。 */
@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfiguration {

    @Bean
    public StorageCredentialProvider storageCredentialProvider(FileStorageProperties properties) {
        return new EnvironmentStorageCredentialProvider(properties);
    }
}
