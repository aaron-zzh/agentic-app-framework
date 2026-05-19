package com.xuejiai.aaf.framework.storage;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;

import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云 OSS 存储实现。
 */
@Slf4j
public class OssStorageService implements StorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties.OssProperties config;
    private final OSS ossClient;

    public OssStorageService(StorageProperties.OssProperties config) {
        this.config = config;
        this.ossClient =
                new OSSClientBuilder()
                        .build(config.endpoint(), config.accessKeyId(), config.accessKeySecret());
    }

    @Override
    public String upload(InputStream input, String filename, String contentType) {
        var ext = extractExtension(filename);
        var key = LocalDate.now().format(DATE_PATH) + "/" + UUID.randomUUID() + ext;
        var metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        ossClient.putObject(config.bucketName(), key, input, metadata);
        log.info("OSS 上传: {}", key);
        return key;
    }

    @Override
    public InputStream download(String key) {
        var obj = ossClient.getObject(config.bucketName(), key);
        return obj.getObjectContent();
    }

    @Override
    public void delete(String key) {
        ossClient.deleteObject(config.bucketName(), key);
        log.info("OSS 删除: {}", key);
    }

    @Override
    public String getUrl(String key) {
        // 生成签名 URL，有效期 1 小时
        var expiration = new Date(System.currentTimeMillis() + 3600 * 1000);
        return ossClient.generatePresignedUrl(config.bucketName(), key, expiration).toString();
    }

    @Override
    public String getPresignedUploadUrl(String key, Duration expiry) {
        var expiration = new Date(System.currentTimeMillis() + expiry.toMillis());
        var url =
                ossClient.generatePresignedUrl(
                        config.bucketName(),
                        key,
                        expiration,
                        com.aliyun.oss.HttpMethod.PUT);
        return url.toString();
    }

    private String extractExtension(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
