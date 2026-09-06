package com.xuejiai.aaf.module.system.file.api;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.module.system.file.enums.FileStoragePurpose;

/** 文件子域对外暴露的上传、查询与引用生命周期边界。 */
public interface FileStoragePort {

    StoredFile uploadCurrent(MultipartFile file);

    StoredFile uploadCurrent(MultipartFile file, FileStoragePurpose storagePurpose);

    StoredFile uploadFromUrl(String url, String path, String contentType, Long uploaderId);

    StoredFile uploadFromBytes(byte[] bytes, String path, String contentType, Long uploaderId);

    StoredFile uploadFromBase64(String base64, String path, Long uploaderId);

    StoredFile get(Long fileId);

    StoredFile getByKey(String key);

    StoredFile requireCurrentOwner(Long fileId);

    StoredFile requireCurrentOwnerByKey(String key);

    /**
     * 将文件 ID 解析为可直接交给外部 AI/SDK 使用的图像数据串，并校验当前用户所有权。
     *
     * <p>按文件绑定的存储类型分流，调用方无需关心底层存储差异：
     *
     * <ul>
     *   <li>本地存储 → {@code data:<mime>;base64,<...>} Data URL（本地文件受权限保护，不能把内部访问
     *       端点直接交给外部服务下载，否则会因缺少用户鉴权而 401）
     *   <li>OSS/远程存储 → 签名下载 URL 或公开直链（外部服务可直接下载）
     * </ul>
     *
     * <p>返回顺序与 {@code fileIds} 一致。
     */
    List<String> resolveImageData(List<Long> fileIds);

    String getAccessibleUrl(Long fileId);

    InputStream openByKey(String key);

    void requestDeleteByKey(String key);

    String prepareExternalAccessByKey(String key, Duration expiry);

    String prepareCurrentOwnerExternalAccessByKey(String key, Duration expiry);

    void retain(Long fileId, FileReference reference);

    void release(Long fileId, FileReference reference);

    void requestDelete(Long fileId);
}
