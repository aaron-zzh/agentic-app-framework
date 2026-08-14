package com.xuejiai.aaf.module.system.file.api;

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

    /** 校验当前用户所有权后返回文件记录。 */
    StoredFile requireCurrentOwner(Long fileId);

    /** 按文件绑定的存储配置准备 AI 图像模型输入。 */
    List<String> prepareCurrentOwnerImageInputs(List<Long> fileIds);

    String getAccessibleUrl(Long fileId);

    void retain(Long fileId, FileReference reference);

    void release(Long fileId, FileReference reference);

    void requestDelete(Long fileId);
}
