package com.xuejiai.aaf.module.system.file.api;

import org.springframework.web.multipart.MultipartFile;

/** 文件子域对外暴露的上传、查询与引用生命周期边界。 */
public interface FileStoragePort {

    StoredFile uploadCurrent(MultipartFile file);

    StoredFile uploadFromUrl(String url, String path, String contentType, Long uploaderId);

    StoredFile uploadFromBytes(byte[] bytes, String path, String contentType, Long uploaderId);

    StoredFile uploadFromBase64(String base64, String path, Long uploaderId);

    StoredFile get(Long fileId);

    String getAccessibleUrl(Long fileId);

    void retain(Long fileId, FileReference reference);

    void release(Long fileId, FileReference reference);

    void requestDelete(Long fileId);
}
