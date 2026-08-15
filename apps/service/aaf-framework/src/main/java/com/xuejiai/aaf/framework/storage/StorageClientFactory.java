package com.xuejiai.aaf.framework.storage;

/** 将 typed spec 与环境凭证组装为底层客户端的工厂 SPI。 */
public interface StorageClientFactory<S extends StorageSpec> {

    StorageType type();

    Class<S> specType();

    StorageClient create(S spec, StorageCredentialProvider credentialProvider);
}
