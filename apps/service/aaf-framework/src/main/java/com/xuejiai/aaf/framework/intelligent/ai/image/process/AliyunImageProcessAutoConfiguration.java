package com.xuejiai.aaf.framework.intelligent.ai.image.process;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aliyun.teaopenapi.models.Config;

import com.xuejiai.aaf.framework.storage.StorageCredentialProvider;

/** 阿里云图像处理 SDK 自动配置，复用 {@code aaf.storage.credentials.oss-default} 凭证。 */
@Configuration
@ConditionalOnClass(name = "com.aliyun.imageenhan20190930.Client")
@ConditionalOnProperty(prefix = "aaf.ai.image-process", name = "enabled", havingValue = "true")
public class AliyunImageProcessAutoConfiguration {

    private static final String CREDENTIAL_REF = "oss-default";

    @Bean
    @ConditionalOnClass(name = "com.aliyun.imageenhan20190930.Client")
    public com.aliyun.imageenhan20190930.Client imageenhanClient(
            StorageCredentialProvider credentialProvider) throws Exception {
        return new com.aliyun.imageenhan20190930.Client(
                clientConfig(credentialProvider, "imageenhan.cn-shanghai.aliyuncs.com"));
    }

    @Bean
    @ConditionalOnClass(name = "com.aliyun.imageseg20191230.Client")
    public com.aliyun.imageseg20191230.Client imagesegClient(
            StorageCredentialProvider credentialProvider) throws Exception {
        return new com.aliyun.imageseg20191230.Client(
                clientConfig(credentialProvider, "imageseg.cn-shanghai.aliyuncs.com"));
    }

    @Bean
    public ImageProcessService imageProcessService(
            com.aliyun.imageenhan20190930.Client imageenhanClient,
            @Autowired(required = false) com.aliyun.imageseg20191230.Client imagesegClient) {
        return new AliyunImageProcessService(imageenhanClient, imagesegClient);
    }

    private Config clientConfig(StorageCredentialProvider credentialProvider, String endpoint) {
        var credential = credentialProvider.require(CREDENTIAL_REF);
        var config =
                new Config()
                        .setAccessKeyId(credential.accessKeyId())
                        .setAccessKeySecret(credential.accessKeySecret());
        config.endpoint = endpoint;
        return config;
    }
}
