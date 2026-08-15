package com.xuejiai.aaf.framework.storage;

/** 本地文件系统规格。 */
public record LocalStorageSpec(String basePath, String urlPrefix) implements StorageSpec {}
