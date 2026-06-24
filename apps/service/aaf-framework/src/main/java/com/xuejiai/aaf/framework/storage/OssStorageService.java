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
import com.aliyun.oss.model.ProcessObjectRequest;
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
        var meta = new com.aliyun.oss.model.ObjectMetadata();
        if (contentType != null && !contentType.isBlank()) {
            meta.setContentType(contentType);
        }
        ossClient.putObject(props.bucketName(), key, input, meta);
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

    @Override
    public String getPresignedDownloadUrl(String key, Duration expiry) {
        var request =
                new GeneratePresignedUrlRequest(
                        props.bucketName(), key, com.aliyun.oss.HttpMethod.GET);
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
                    region,
                    props.urlPrefix());
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

    /**
     * 从访问 URL 反推存储 key（去掉 urlPrefix）。
     *
     * @return key；无法解析时返回 null
     */
    public String urlToKey(String url) {
        if (url == null) return null;
        var prefix = props.urlPrefix();
        if (prefix != null && !prefix.isBlank()) {
            String base = prefix.endsWith("/") ? prefix : prefix + "/";
            if (url.startsWith(base)) return url.substring(base.length());
        }
        // fallback：去掉 https://bucket.endpoint/ 前缀
        String base = "https://" + props.bucketName() + "." + props.endpoint() + "/";
        if (url.startsWith(base)) return url.substring(base.length());
        return null;
    }

    /**
     * 对 OSS 上已存在的视频 key 截取第一帧，另存为 jpg 缩略图，返回缩略图 key。
     *
     * <p>调用 OSS 数据处理接口持久化截帧结果，只产生一次处理费用，后续直接访问缩略图 URL。
     *
     * @param videoKey 视频对象 key，如 {@code aigc/video/xxx.mp4}
     * @return 缩略图 key，如 {@code aigc/video/xxx_thumb.jpg}；失败时返回 null
     */
    public String generateVideoThumbnail(String videoKey) {
        try {
            String thumbKey = videoKey.replaceAll("\\.[^.]+$", "") + "_thumb.jpg";
            // OSS 视频截帧：t=0ms 第一帧，f=jpg，保持原始分辨率，m_fast 快速关键帧
            String process = "video/snapshot,t_0,f_jpg,w_0,h_0,m_fast";
            // base64 编码目标 key（OSS saveas 要求）
            String targetBase64 =
                    java.util.Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    thumbKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String fullProcess =
                    process
                            + "|sys/saveas,o_"
                            + targetBase64
                            + ",b_"
                            + java.util.Base64.getUrlEncoder()
                                    .withoutPadding()
                                    .encodeToString(
                                            props.bucketName()
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8));
            ossClient.processObject(
                    new ProcessObjectRequest(props.bucketName(), videoKey, fullProcess));
            log.info("[OSS] 视频截帧完成: videoKey={}, thumbKey={}", videoKey, thumbKey);
            return thumbKey;
        } catch (Exception e) {
            log.warn("[OSS] 视频截帧失败（降级为无缩略图）: videoKey={}, err={}", videoKey, e.getMessage());
            return null;
        }
    }
}
