package com.xuejiai.aaf.framework.storage;

/** S3 兼容存储客户端工厂。 */
public final class S3StorageClientFactory implements StorageClientFactory<S3StorageSpec> {

    @Override
    public StorageType type() {
        return StorageType.S3;
    }

    @Override
    public Class<S3StorageSpec> specType() {
        return S3StorageSpec.class;
    }

    @Override
    public StorageClient create(S3StorageSpec spec, StorageCredentialProvider credentialProvider) {
        if (spec == null
                || spec.endpoint() == null
                || spec.endpoint().isBlank()
                || spec.bucketName() == null
                || spec.bucketName().isBlank()
                || spec.enablePublicAccess() == null
                || spec.downloadUrlExpirySeconds() == null) {
            throw new StorageException("S3 存储规格不完整", null);
        }
        return new S3StorageService(spec, credentialProvider.require(spec.credentialRef()));
    }
}
