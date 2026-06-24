package com.xuejiai.aaf.framework.intelligent.ai.image.process;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aliyun.teaopenapi.models.Config;

/** 阿里云图像处理 SDK 自动配置，复用 {@code aaf.storage.oss} 凭证。 */
@Configuration
@ConditionalOnClass(name = "com.aliyun.imageenhan20190930.Client")
public class AliyunImageProcessAutoConfiguration {

    @Value("${aaf.storage.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aaf.storage.oss.access-key-secret}")
    private String accessKeySecret;

    @Bean
    @ConditionalOnClass(name = "com.aliyun.imageenhan20190930.Client")
    public com.aliyun.imageenhan20190930.Client imageenhanClient() throws Exception {
        var config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret);
        config.endpoint = "imageenhan.cn-shanghai.aliyuncs.com";
        return new com.aliyun.imageenhan20190930.Client(config);
    }

    @Bean
    @ConditionalOnClass(name = "com.aliyun.imageseg20191230.Client")
    public com.aliyun.imageseg20191230.Client imagesegClient() throws Exception {
        var config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret);
        config.endpoint = "imageseg.cn-shanghai.aliyuncs.com";
        return new com.aliyun.imageseg20191230.Client(config);
    }

    @Bean
    public ImageProcessService imageProcessService(
            com.aliyun.imageenhan20190930.Client imageenhanClient,
            @Autowired(required = false) com.aliyun.imageseg20191230.Client imagesegClient) {
        return new AliyunImageProcessService(imageenhanClient, imagesegClient);
    }
}
