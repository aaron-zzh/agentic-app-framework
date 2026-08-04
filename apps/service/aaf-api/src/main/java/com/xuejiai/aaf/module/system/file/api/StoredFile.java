package com.xuejiai.aaf.module.system.file.api;

/** 已登记的物理文件；URL 为按当前存储配置动态解析的访问地址。 */
public record StoredFile(
        Long fileId,
        String key,
        String url,
        String originalName,
        String mimeType,
        long size,
        String contentHash,
        Long uploaderId) {}
