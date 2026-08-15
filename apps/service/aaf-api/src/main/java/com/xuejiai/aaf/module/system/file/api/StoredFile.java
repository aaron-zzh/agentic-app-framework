package com.xuejiai.aaf.module.system.file.api;

/** 已登记的物理文件；URL 始终指向 FileAccessService 受控访问端点。 */
public record StoredFile(
        Long fileId,
        String key,
        String url,
        String originalName,
        String mimeType,
        long size,
        String contentHash,
        Long uploaderId) {}
