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
                || spec.urlPrefix() == null
                || spec.urlPrefix().isBlank()) {
            throw new StorageException("本地存储必须配置 basePath 和对象访问 URL 前缀", null);
        }
        return new LocalStorageService(spec);
    }
}
