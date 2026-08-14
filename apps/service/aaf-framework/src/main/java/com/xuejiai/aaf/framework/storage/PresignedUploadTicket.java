package com.xuejiai.aaf.framework.storage;

/**
 * 预签名上传票据（M29）。
 *
 * <p>key 由服务端生成，调用方从票据里读取用于后续落库，不再自行拼 key。
 *
 * @param key 服务端生成的对象 key
 * @param url 预签名 PUT URL
 * @param contentType 签名绑定的 MIME 类型，客户端 PUT 时必须一致
 * @param maxSizeBytes 签名绑定的大小上限
 * @param storageConfigId 签发此票据的不可变存储配置 ID
 */
public record PresignedUploadTicket(
        String key, String url, String contentType, long maxSizeBytes, Long storageConfigId) {

    public PresignedUploadTicket(String key, String url, String contentType, long maxSizeBytes) {
        this(key, url, contentType, maxSizeBytes, null);
    }
}
