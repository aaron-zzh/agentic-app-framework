package com.xuejiai.aaf.framework.storage;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;

import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云 OSS 原生存储实现（支持 STS 临时凭证）。
 *
 * <p>使用永久 AccessKey 操作 OSS，并提供 getStsCredentials() 供前端直传分片上传使用。
 */
@Slf4j
public class OssStorageService implements StorageService {

    private final StorageProperties.OssProperties props;
    private final OSS ossClient;
    private final DefaultAcsClient stsClient;

    public OssStorageService(StorageProperties.OssProperties props) {
        this.props = props;
        this.ossClient =
                OSSClientBuilder.create()
                        .endpoint(props.endpoint())
                        .credentialsProvider(
                                new com.aliyun.oss.common.auth.DefaultCredentialProvider(
                                        props.accessKeyId(), props.accessKeySecret()))
                        .build();
        var profile = DefaultProfile.getProfile("", props.accessKeyId(), props.accessKeySecret());
        DefaultProfile.addEndpoint("", "Sts", props.stsEndpointOrDefault());
        this.stsClient = new DefaultAcsClient(profile);
    }

    @Override
    public String upload(InputStream input, String filename, String contentType) {
        var key = buildKey(filename);
        ossClient.putObject(props.bucketName(), key, input);
        return key;
    }

    @Override
    public InputStream download(String key) {
        OSSObject obj = ossClient.getObject(props.bucketName(), key);
        return obj.getObjectContent();
    }

    @Override
    public void delete(String key) {
        ossClient.deleteObject(props.bucketName(), key);
    }

    @Override
    public String getUrl(String key) {
        var prefix = props.urlPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix.endsWith("/") ? prefix + key : prefix + "/" + key;
        }
        return "https://" + props.bucketName() + "." + props.endpoint() + "/" + key;
    }

    @Override
    public String getPresignedUploadUrl(String key, Duration expiry) {
        var request =
                new GeneratePresignedUrlRequest(
                        props.bucketName(), key, com.aliyun.oss.HttpMethod.PUT);
        request.setExpiration(new Date(System.currentTimeMillis() + expiry.toMillis()));
        return ossClient.generatePresignedUrl(request).toString();
    }

    /**
     * 获取 STS 临时凭证（供前端 OSS SDK 直传分片上传使用）。
     *
     * <p>官方建议：高并发场景复用凭证直至过期，不要每次请求都调用。 业务层应在 Expiration 前 5 分钟刷新。
     */
    public StsCredentials getStsCredentials() {
        try {
            var request = new AssumeRoleRequest();
            request.setSysMethod(MethodType.POST);
            request.setRoleArn(props.roleArn());
            request.setRoleSessionName("aaf-upload-" + System.currentTimeMillis());
            request.setDurationSeconds((long) props.durationSecondsOrDefault());

            AssumeRoleResponse response = stsClient.getAcsResponse(request);
            var cred = response.getCredentials();

            // 从 endpoint 提取 region，如 oss-cn-hangzhou.aliyuncs.com → oss-cn-hangzhou
            var region =
                    props.endpoint().contains(".")
                            ? props.endpoint().substring(0, props.endpoint().indexOf('.'))
                            : props.endpoint();

            return new StsCredentials(
                    cred.getAccessKeyId(),
                    cred.getAccessKeySecret(),
                    cred.getSecurityToken(),
                    cred.getExpiration(),
                    props.bucketName(),
                    props.endpoint(),
                    region);
        } catch (ClientException e) {
            throw new StorageException("获取 STS 临时凭证失败: " + e.getErrMsg(), e);
        }
    }

    private String buildKey(String filename) {
        var date = LocalDate.now();
        var ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
        return String.format(
                "uploads/%d/%02d/%02d/%s%s",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth(), UUID.randomUUID(), ext);
    }
}
