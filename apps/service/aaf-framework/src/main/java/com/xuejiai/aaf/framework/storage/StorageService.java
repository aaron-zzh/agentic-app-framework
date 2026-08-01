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
     * <p>B13：实现内部必须调用 {@link UploadPolicy#assertNotActiveContent} 兜底——业务校验在 {@link FileService}，
     * 但直接注入 StorageService 的调用方会绕过它，主动内容拒绝要在存储层再拦一次。
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
     * 获取预签名上传票据（前端直传用）。
     *
     * <p>M29：不再接受调用方的裸 key——key 由 {@link PresignedUploadRequest#toKey()} 按 owner 命名空间生成，
     * 通用接口无法被误用来签别人的命名空间。
     *
     * <p>m20：实现必须把 {@code contentType} 与大小上限写入签名约束。
     *
     * @param request 预签名请求（含归属、文件名、类型、大小上限、有效期）
     * @return 票据（含服务端生成的 key 与签名 URL）
     */
    PresignedUploadTicket getPresignedUploadUrl(PresignedUploadRequest request);

    /**
     * 获取预签名下载 URL（GET）。
     *
     * <p>供外部服务（如 AI 视觉模型）从私有桶按 URL 直接读取文件，不依赖公开访问或 CDN。
     *
     * <p>OSS 实现走原生 OSS 域名签名直链，绕过可能存在的 CDN 防盗链；本地/通用实现回退为 {@link #getUrl(String)}。
     *
     * @param key 文件 key
     * @param expiry 有效期，建议 1 小时（视觉模型典型调用 < 1 分钟，留余量给重试）
     * @return 带签名参数的可公网下载 URL
     */
    String getPresignedDownloadUrl(String key, Duration expiry);
}
