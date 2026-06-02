package com.xuejiai.aaf.framework.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储服务自动配置。
 *
 * <p>根据 aaf.storage.type 条件注册对应实现 Bean。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(prefix = "aaf.storage", name = "type")
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "aaf.storage", name = "type", havingValue = "local")
    public StorageService localStorageService(StorageProperties properties) {
        return new LocalStorageService(properties.local());
    }

    @Bean
    @ConditionalOnProperty(prefix = "aaf.storage", name = "type", havingValue = "s3")
    public StorageService s3StorageService(StorageProperties properties) {
        return new S3StorageService(properties.s3());
    }

    @Bean
    public ImageProcessor imageProcessor() {
        return new ImageProcessor();
    }

    @Bean
    public FileService fileService(
            StorageService storageService,
            ImageProcessor imageProcessor,
            StorageProperties properties) {
        return new FileService(storageService, imageProcessor, properties.uploadOrDefault());
    }
}
