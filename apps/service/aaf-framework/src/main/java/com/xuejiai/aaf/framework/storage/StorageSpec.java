package com.xuejiai.aaf.framework.storage;

/** 不包含真实凭证的持久化存储规格。 */
public sealed interface StorageSpec permits LocalStorageSpec, S3StorageSpec, OssStorageSpec {}
