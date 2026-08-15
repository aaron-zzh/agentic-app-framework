package com.xuejiai.aaf.module.system.file.config;

import java.util.Map;

import com.xuejiai.aaf.framework.storage.StorageCredential;
import com.xuejiai.aaf.framework.storage.StorageCredentialProvider;
import com.xuejiai.aaf.framework.storage.StorageException;

/** 从应用环境配置解析 credentialRef。 */
public final class EnvironmentStorageCredentialProvider implements StorageCredentialProvider {

    private final Map<String, FileStorageProperties.CredentialProperties> credentials;

    public EnvironmentStorageCredentialProvider(FileStorageProperties properties) {
        this.credentials =
                properties.credentials() == null ? Map.of() : Map.copyOf(properties.credentials());
    }

    @Override
    public StorageCredential require(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            throw new StorageException("存储配置缺少 credentialRef", null);
        }
        var credential = credentials.get(credentialRef);
        if (credential == null
                || credential.accessKeyId() == null
                || credential.accessKeyId().isBlank()
                || credential.accessKeySecret() == null
                || credential.accessKeySecret().isBlank()) {
            throw new StorageException("存储凭证不存在或不完整: " + credentialRef, null);
        }
        return new StorageCredential(credential.accessKeyId(), credential.accessKeySecret());
    }
}
