package com.xuejiai.aaf.framework.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储配置属性。
 *
 * <p>通过 aaf.storage.type 切换存储后端。
 */
@ConfigurationProperties(prefix = "aaf.storage")
public record StorageProperties(
        StorageType type,
        LocalProperties local,
        OssProperties oss,
        MinioProperties minio) {

    /** 存储类型枚举 */
    public enum StorageType {
        LOCAL,
        OSS,
        MINIO
    }

    /** 本地存储配置 */
    public record LocalProperties(String basePath, String urlPrefix) {}

    /** 阿里云 OSS 配置 */
    public record OssProperties(
            String endpoint,
            String accessKeyId,
            String accessKeySecret,
            String bucketName,
            String stsRoleArn) {}

    /** MinIO 配置 */
    public record MinioProperties(
            String endpoint, String accessKey, String secretKey, String bucketName) {}
}
