package com.xuejiai.aaf.framework.storage;

/** 由环境配置解析的对象存储真实凭证。 */
public record StorageCredential(String accessKeyId, String accessKeySecret) {}
