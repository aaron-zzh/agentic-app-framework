package com.xuejiai.aaf.framework.storage;

import java.time.Duration;

/** 阿里云 OSS 规格，真实凭证由 credentialRef 解析。 */
public record OssStorageSpec(
        String endpoint,
        String bucketName,
        String roleArn,
        String stsEndpoint,
        String domain,
        Boolean enablePublicAccess,
        Integer downloadUrlExpirySeconds,
        Integer durationSeconds,
        String credentialRef)
        implements StorageSpec {

    public String stsEndpointOrDefault() {
        return stsEndpoint != null && !stsEndpoint.isBlank() ? stsEndpoint : "sts.aliyuncs.com";
    }

    public int durationSecondsOrDefault() {
        return durationSeconds != null ? durationSeconds : 3600;
    }

    public boolean publicAccessEnabled() {
        return Boolean.TRUE.equals(enablePublicAccess);
    }

    public Duration downloadUrlExpiry() {
        return Duration.ofSeconds(downloadUrlExpirySeconds);
    }

    public String domainOrDefault() {
        if (domain != null && !domain.isBlank()) {
            return stripTrailingSlash(domain);
        }
        var endpointHost = endpoint.trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
        return "https://" + bucketName + "." + endpointHost;
    }

    private String stripTrailingSlash(String value) {
        return value.trim().replaceAll("/+$", "");
    }
}
