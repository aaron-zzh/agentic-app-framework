package com.xuejiai.aaf.framework.storage;

/** 按 credentialRef 解析真实凭证的上层扩展点。 */
@FunctionalInterface
public interface StorageCredentialProvider {

    StorageCredential require(String credentialRef);
}
