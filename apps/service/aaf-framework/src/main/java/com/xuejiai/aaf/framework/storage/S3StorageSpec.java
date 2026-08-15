package com.xuejiai.aaf.framework.storage;

import java.time.Duration;

/** S3 兼容存储规格，真实凭证由 credentialRef 解析。 */
public record S3StorageSpec(
        String endpoint,
        String bucketName,
        String region,
        String domain,
        Boolean enablePublicAccess,
        Integer downloadUrlExpirySeconds,
        String credentialRef)
        implements StorageSpec {

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
        return stripTrailingSlash(endpoint) + "/" + bucketName;
    }

    private String stripTrailingSlash(String value) {
        return value.trim().replaceAll("/+$", "");
    }
}
