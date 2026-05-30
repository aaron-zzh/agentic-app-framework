package com.xuejiai.aaf.framework.storage;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储配置属性。
 *
 * <p>通过 aaf.storage.type 切换存储后端。S3 配置兼容 MinIO / 阿里云 OSS / AWS S3。
 */
@ConfigurationProperties(prefix = "aaf.storage")
public record StorageProperties(
        StorageType type, LocalProperties local, S3Properties s3, UploadLimits upload) {

    /** 存储类型枚举 */
    public enum StorageType {
        LOCAL,
        S3
    }

    /** 本地存储配置 */
    public record LocalProperties(String basePath, String urlPrefix) {}

    /** S3 兼容存储配置（MinIO / 阿里云 OSS / AWS S3） */
    public record S3Properties(
            String endpoint,
            String accessKey,
            String secretKey,
            String bucketName,
            String region) {}

    /** 上传约束配置 */
    public record UploadLimits(Set<String> allowedContentTypes, long maxSizeBytes) {
        /** 默认白名单 + 10MB */
        public static UploadLimits defaults() {
            return new UploadLimits(
                    Set.of(
                            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml",
                            "application/pdf",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "text/plain", "text/csv", "text/markdown"),
                    10L * 1024 * 1024);
        }
    }

    /** 获取上传约束（未配置时使用默认值） */
    public UploadLimits uploadOrDefault() {
        return upload != null ? upload : UploadLimits.defaults();
    }
}
