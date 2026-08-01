package com.xuejiai.aaf.framework.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 兼容存储实现（MinIO / 阿里云 OSS / AWS S3）。
 *
 * <p>通过 AWS S3 SDK 统一访问，配置 endpoint 切换后端。
 */
@Slf4j
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
        // B13：存储层兜底拒绝主动内容，防止绕过 FileService 直调
        UploadPolicy.assertNotActiveContent(filename, contentType);
        var key = generateKey(filename);
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("aaf-s3-upload-", ".tmp");
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            var request =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .build();
            s3Client.putObject(request, RequestBody.fromFile(tempFile));
            return key;
        } catch (Exception e) {
            throw new StorageException("文件上传失败: " + e.getMessage(), e);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("S3 上传临时文件清理失败: {}", tempFile, e);
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

    /**
     * m20：预签名 PUT 固定 contentType 与大小范围。
     *
     * <p>S3 预签名 PUT 的约束通过被签名的 {@code PutObjectRequest} 表达：签名覆盖 Content-Type 与 Content-Length，
     * 客户端改动其中任一项都会导致签名校验失败，从而无法向该 key 上传任意内容或超大对象。
     */
    @Override
    public PresignedUploadTicket getPresignedUploadUrl(PresignedUploadRequest req) {
        var key = req.toKey();
        var presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(req.expiry())
                        .putObjectRequest(
                                r ->
                                        r.bucket(bucketName)
                                                .key(key)
                                                .contentType(req.contentType())
                                                .contentLength(req.maxSizeBytes()))
                        .build();
        var url = presigner.presignPutObject(presignRequest).url().toString();
        return new PresignedUploadTicket(key, url, req.contentType(), req.maxSizeBytes());
    }

    @Override
    public String getPresignedDownloadUrl(String key, Duration expiry) {
        var request =
                GetObjectPresignRequest.builder()
                        .signatureDuration(expiry)
                        .getObjectRequest(r -> r.bucket(bucketName).key(key))
                        .build();
        return presigner.presignGetObject(request).url().toString();
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
