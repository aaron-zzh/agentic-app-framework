package com.xuejiai.aaf.framework.storage;

import java.io.InputStream;
import java.time.Duration;

/** 由动态存储配置创建的底层对象存储客户端。 */
public interface StorageClient extends AutoCloseable {

    String upload(InputStream input, String filename, String contentType);

    InputStream download(String key);

    void delete(String key);

    /** 返回存储提供方地址；浏览器访问必须由业务层 FileAccessService 统一代理。 */
    String getUrl(String key);

    PresignedUploadTicket getPresignedUploadUrl(PresignedUploadRequest request);

    String getPresignedDownloadUrl(String key, Duration expiry);

    @Override
    default void close() {}
}
