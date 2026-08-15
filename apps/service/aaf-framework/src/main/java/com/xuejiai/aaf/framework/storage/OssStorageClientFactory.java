package com.xuejiai.aaf.framework.storage;

/** 阿里云 OSS 存储客户端工厂。 */
public final class OssStorageClientFactory implements StorageClientFactory<OssStorageSpec> {

    @Override
    public StorageType type() {
        return StorageType.OSS;
    }

    @Override
    public Class<OssStorageSpec> specType() {
        return OssStorageSpec.class;
    }

    @Override
    public StorageClient create(OssStorageSpec spec, StorageCredentialProvider credentialProvider) {
        if (spec == null
                || spec.endpoint() == null
                || spec.endpoint().isBlank()
                || spec.bucketName() == null
                || spec.bucketName().isBlank()) {
            throw new StorageException("OSS 存储规格不完整", null);
        }
        return new OssStorageService(spec, credentialProvider.require(spec.credentialRef()));
    }
}
