package com.xuejiai.aaf.framework.storage;

import java.io.InputStream;
import java.time.Duration;

/** 由动态存储配置创建的底层对象存储客户端。 */
public interface StorageClient extends AutoCloseable {

    String upload(InputStream input, String filename, String contentType);

    InputStream download(String key);

    void delete(String key);

    /** 返回存储配置对应的稳定公开地址；私有访问由业务层按需生成预签名 URL。 */
    String getUrl(String key);

    PresignedUploadTicket getPresignedUploadUrl(PresignedUploadRequest request);

    String getPresignedDownloadUrl(String key, Duration expiry);

    @Override
    default void close() {}
}
