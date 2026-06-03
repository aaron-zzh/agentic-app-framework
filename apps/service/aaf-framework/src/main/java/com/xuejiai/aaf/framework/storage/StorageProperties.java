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
        StorageType type,
        LocalProperties local,
        S3Properties s3,
        OssProperties oss,
        UploadLimits upload) {

    /** 存储类型枚举 */
    public enum StorageType {
        LOCAL,
        S3,
        OSS
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

    /** 阿里云 OSS 原生配置（支持 STS 临时凭证） */
    public record OssProperties(
            /** OSS endpoint，如 oss-cn-hangzhou.aliyuncs.com */
            String endpoint,
            /** Bucket 名称 */
            String bucketName,
            /** RAM 用户 AccessKeyId（用于调用 STS） */
            String accessKeyId,
            /** RAM 用户 AccessKeySecret */
            String accessKeySecret,
            /** RAM 角色 ARN，如 acs:ram::uid:role/role-name */
            String roleArn,
            /** STS endpoint，默认 sts.aliyuncs.com */
            String stsEndpoint,
            /** 对象访问 URL 前缀，如 https://bucket.oss-cn-hangzhou.aliyuncs.com */
            String urlPrefix,
            /** STS 凭证有效期（秒），最小 900，默认 3600 */
            Integer durationSeconds) {

        public String stsEndpointOrDefault() {
            return stsEndpoint != null ? stsEndpoint : "sts.aliyuncs.com";
        }

        public int durationSecondsOrDefault() {
            return durationSeconds != null ? durationSeconds : 3600;
        }
    }

    /** 上传约束配置 */
    public record UploadLimits(Set<String> allowedContentTypes, long maxSizeBytes) {
        /** 默认白名单 + 10MB */
        public static UploadLimits defaults() {
            return new UploadLimits(
                    Set.of(
                            "image/jpeg",
                            "image/png",
                            "image/gif",
                            "image/webp",
                            "image/svg+xml",
                            "application/pdf",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "text/plain",
                            "text/csv",
                            "text/markdown"),
                    10L * 1024 * 1024);
        }
    }

    /** 获取上传约束（未配置时使用默认值） */
    public UploadLimits uploadOrDefault() {
        return upload != null ? upload : UploadLimits.defaults();
    }
}
