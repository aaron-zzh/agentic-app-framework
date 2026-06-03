package com.xuejiai.aaf.framework.storage;

/**
 * OSS STS 临时凭证响应。
 *
 * @param accessKeyId STS 临时 AccessKeyId（以 STS. 开头）
 * @param accessKeySecret STS 临时 AccessKeySecret
 * @param securityToken STS 安全令牌
 * @param expiration 过期时间（ISO 8601 UTC，如 2024-04-18T11:33:40Z）
 * @param bucket OSS Bucket 名称
 * @param endpoint OSS Endpoint
 * @param region OSS 地域，如 oss-cn-hangzhou
 */
public record StsCredentials(
        String accessKeyId,
        String accessKeySecret,
        String securityToken,
        String expiration,
        String bucket,
        String endpoint,
        String region) {}
