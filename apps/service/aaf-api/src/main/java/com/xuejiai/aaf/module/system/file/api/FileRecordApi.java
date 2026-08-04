package com.xuejiai.aaf.module.system.file.api;

/** 文件子域对其他业务模块暴露的稳定接口。 */
public interface FileRecordApi {

    StoredFile registerCurrent(
            String key,
            String originalName,
            String mimeType,
            long size,
            String contentHash);

    StoredFile register(
            String key,
            String originalName,
            String mimeType,
            long size,
            String contentHash,
            Long uploaderId);

    StoredFile get(Long fileId);

    String getAccessibleUrl(Long fileId);

    void retain(Long fileId, FileReference reference);

    void release(Long fileId, FileReference reference);

    void requestDelete(Long fileId);
}
