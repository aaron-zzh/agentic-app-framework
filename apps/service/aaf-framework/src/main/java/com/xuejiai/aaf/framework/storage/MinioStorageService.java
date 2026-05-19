package com.xuejiai.aaf.framework.storage;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.minio.*;
import io.minio.http.Method;

import lombok.extern.slf4j.Slf4j;

/**
 * MinIO 存储实现。
 *
 * <p>启动时自动检查并创建 bucket。
 */
@Slf4j
public class MinioStorageService implements StorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties.MinioProperties config;
    private final MinioClient minioClient;

    public MinioStorageService(StorageProperties.MinioProperties config) {
        this.config = config;
        this.minioClient =
                MinioClient.builder()
                        .endpoint(config.endpoint())
                        .credentials(config.accessKey(), config.secretKey())
                        .build();
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            var exists =
                    minioClient.bucketExists(
                            BucketExistsArgs.builder().bucket(config.bucketName()).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(config.bucketName()).build());
                log.info("MinIO bucket 已创建: {}", config.bucketName());
            }
        } catch (Exception e) {
            throw new StorageException("MinIO bucket 检查/创建失败", e);
        }
    }

    @Override
    public String upload(InputStream input, String filename, String contentType) {
        var ext = extractExtension(filename);
        var key = LocalDate.now().format(DATE_PATH) + "/" + UUID.randomUUID() + ext;
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(config.bucketName())
                            .object(key)
                            .stream(input, -1, 10485760)
                            .contentType(contentType)
                            .build());
        } catch (Exception e) {
            throw new StorageException("MinIO 上传失败", e);
        }
        log.info("MinIO 上传: {}", key);
        return key;
    }

    @Override
    public InputStream download(String key) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder().bucket(config.bucketName()).object(key).build());
        } catch (Exception e) {
            throw new StorageException("MinIO 下载失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(config.bucketName()).object(key).build());
        } catch (Exception e) {
            throw new StorageException("MinIO 删除失败: " + key, e);
        }
        log.info("MinIO 删除: {}", key);
    }

    @Override
    public String getUrl(String key) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(config.bucketName())
                            .object(key)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            throw new StorageException("MinIO 获取 URL 失败: " + key, e);
        }
    }

    @Override
    public String getPresignedUploadUrl(String key, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(config.bucketName())
                            .object(key)
                            .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new StorageException("MinIO 获取预签名上传 URL 失败: " + key, e);
        }
    }

    private String extractExtension(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
