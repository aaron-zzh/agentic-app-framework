package com.xuejiai.aaf.module.system.file.api;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/** 文件子域对其他业务模块暴露的稳定接口。 */
public interface FileRecordApi {

    StoredFile registerCurrent(
            String key, String originalName, String mimeType, long size, String contentHash);

    StoredFile register(
            String key,
            String originalName,
            String mimeType,
            long size,
            String contentHash,
            Long uploaderId);

    StoredFile get(Long fileId);

    StoredFile getByKey(String key);

    /** 校验当前用户所有权后返回文件记录。 */
    StoredFile requireCurrentOwner(Long fileId);

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

    void retain(Long fileId, FileReference reference);

    void release(Long fileId, FileReference reference);

    void requestDelete(Long fileId);
}
