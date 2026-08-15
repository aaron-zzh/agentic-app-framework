package com.xuejiai.aaf.framework.storage;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** 存储 SPI 自动配置；只注册无状态工厂，不创建任何默认运行时客户端。 */
@AutoConfiguration
public class StorageAutoConfiguration {

    @Bean
    public StorageClientFactory<LocalStorageSpec> localStorageClientFactory() {
        return new LocalStorageClientFactory();
    }

    @Bean
    public StorageClientFactory<S3StorageSpec> s3StorageClientFactory() {
        return new S3StorageClientFactory();
    }

    @Bean
    public StorageClientFactory<OssStorageSpec> ossStorageClientFactory() {
        return new OssStorageClientFactory();
    }

    @Bean
    public ImageProcessor imageProcessor() {
        return new ImageProcessor();
    }
}
