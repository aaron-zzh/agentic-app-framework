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
 * @param urlPrefix 对象访问 URL 前缀（可空）；前端拼上传后访问 URL 时优先使用，
 *     用于绑定自定义域名 / CDN 加速域名场景；为空则回退 https://&lt;bucket&gt;.&lt;endpoint&gt;/&lt;key&gt;
 */
public record StsCredentials(
        String accessKeyId,
        String accessKeySecret,
        String securityToken,
        String expiration,
        String bucket,
        String endpoint,
        String region,
        String urlPrefix) {}
