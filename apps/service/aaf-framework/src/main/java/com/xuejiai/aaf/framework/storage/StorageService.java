package com.xuejiai.aaf.framework.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * 文件存储服务统一接口。
 *
 * <p>支持多后端实现（本地文件系统、阿里云 OSS、MinIO），通过配置切换。
 */
public interface StorageService {

    /**
     * 上传文件。
     *
     * @param input 文件输入流
     * @param filename 原始文件名
     * @param contentType MIME 类型
     * @return 文件 key（存储路径标识）
     */
    String upload(InputStream input, String filename, String contentType);

    /**
     * 下载文件。
     *
     * @param key 文件 key
     * @return 文件输入流
     */
    InputStream download(String key);

    /**
     * 删除文件。
     *
     * @param key 文件 key
     */
    void delete(String key);

    /**
     * 获取文件访问 URL。
     *
     * @param key 文件 key
     * @return 访问 URL
     */
    String getUrl(String key);

    /**
     * 获取预签名上传 URL（前端直传用）。
     *
     * @param key 文件 key
     * @param expiry 有效期
     * @return 预签名 PUT URL
     */
    String getPresignedUploadUrl(String key, Duration expiry);
}
