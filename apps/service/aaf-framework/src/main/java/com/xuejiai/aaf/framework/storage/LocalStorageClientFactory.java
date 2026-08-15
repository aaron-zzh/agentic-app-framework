package com.xuejiai.aaf.framework.storage;

/** 本地存储客户端工厂。 */
public final class LocalStorageClientFactory implements StorageClientFactory<LocalStorageSpec> {

    @Override
    public StorageType type() {
        return StorageType.LOCAL;
    }

    @Override
    public Class<LocalStorageSpec> specType() {
        return LocalStorageSpec.class;
    }

    @Override
    public StorageClient create(
            LocalStorageSpec spec, StorageCredentialProvider credentialProvider) {
        if (spec == null
                || spec.basePath() == null
                || spec.basePath().isBlank()
                || spec.domain() == null
                || spec.domain().isBlank()) {
            throw new StorageException("本地存储必须配置 basePath 和后端访问 domain", null);
        }
        return new LocalStorageService(spec);
    }
}
