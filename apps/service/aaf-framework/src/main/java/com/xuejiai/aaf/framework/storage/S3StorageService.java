package com.xuejiai.aaf.framework.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 兼容存储实现（MinIO / 阿里云 OSS / AWS S3）。
 *
 * <p>通过 AWS S3 SDK 统一访问，配置 endpoint 切换后端。
 */
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucketName;
    private final String endpoint;

    public S3StorageService(StorageProperties.S3Properties props) {
        var credentials =
                StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        var region = Region.of(props.region() != null ? props.region() : "us-east-1");
        var endpointUri = URI.create(props.endpoint());

        this.s3Client =
                S3Client.builder()
                        .endpointOverride(endpointUri)
                        .region(region)
                        .credentialsProvider(credentials)
                        .forcePathStyle(true)
                        .build();

        this.presigner =
                S3Presigner.builder()
                        .endpointOverride(endpointUri)
                        .region(region)
                        .credentialsProvider(credentials)
                        .build();

        this.bucketName = props.bucketName();
        this.endpoint = props.endpoint();
    }

    @Override
    public String upload(InputStream input, String filename, String contentType) {
        var key = generateKey(filename);
        try {
            var request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .build();
            s3Client.putObject(request, RequestBody.fromInputStream(input, input.available()));
            return key;
        } catch (Exception e) {
            throw new StorageException("文件上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String key) {
        var request = GetObjectRequest.builder().bucket(bucketName).key(key).build();
        return s3Client.getObject(request);
    }

    @Override
    public void delete(String key) {
        var request = DeleteObjectRequest.builder().bucket(bucketName).key(key).build();
        s3Client.deleteObject(request);
    }

    @Override
    public String getUrl(String key) {
        return endpoint + "/" + bucketName + "/" + key;
    }

    @Override
    public String getPresignedUploadUrl(String key, Duration expiry) {
        var request =
                PutObjectPresignRequest.builder()
                        .signatureDuration(expiry)
                        .putObjectRequest(r -> r.bucket(bucketName).key(key))
                        .build();
        return presigner.presignPutObject(request).url().toString();
    }

    private String generateKey(String filename) {
        var ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
        var date = LocalDate.now();
        return "%d/%02d/%02d/%s%s"
                .formatted(
                        date.getYear(),
                        date.getMonthValue(),
                        date.getDayOfMonth(),
                        UUID.randomUUID(),
                        ext);
    }
}
