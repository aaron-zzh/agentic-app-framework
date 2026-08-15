package com.xuejiai.aaf.framework.storage;

/** S3 兼容存储规格，真实凭证由 credentialRef 解析。 */
public record S3StorageSpec(String endpoint, String bucketName, String region, String credentialRef)
        implements StorageSpec {}
