package com.xuejiai.aaf.framework.storage;

/** 阿里云 OSS 规格，真实凭证由 credentialRef 解析。 */
public record OssStorageSpec(
        String endpoint,
        String bucketName,
        String roleArn,
        String stsEndpoint,
        String urlPrefix,
        Integer durationSeconds,
        String credentialRef)
        implements StorageSpec {

    public String stsEndpointOrDefault() {
        return stsEndpoint != null && !stsEndpoint.isBlank() ? stsEndpoint : "sts.aliyuncs.com";
    }

    public int durationSecondsOrDefault() {
        return durationSeconds != null ? durationSeconds : 3600;
    }
}
