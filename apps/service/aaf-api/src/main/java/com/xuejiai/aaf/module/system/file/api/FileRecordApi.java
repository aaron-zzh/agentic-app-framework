package com.xuejiai.aaf.module.system.file.api;

/** 文件子域对其他业务模块暴露的登记接口。 */
public interface FileRecordApi {

    SourceFile registerCurrent(String key, String originalName, String mimeType, long size);

    record SourceFile(
            Long id,
            String key,
            String originalName,
            String mimeType,
            long size,
            Long uploaderId) {}
}
