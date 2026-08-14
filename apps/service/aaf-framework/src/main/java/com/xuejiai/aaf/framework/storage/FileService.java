package com.xuejiai.aaf.framework.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件服务门面。在 StorageService 之上提供业务级文件操作。
 *
 * <p>全部上传入口共享 {@link UploadPolicy} 校验，并返回物理对象的稳定 key 与内容哈希。
 */
@Slf4j
public class FileService {

    private final StorageService storageService;
    private final UploadPolicy uploadPolicy;

    public FileService(StorageService storageService, StorageProperties.UploadLimits uploadLimits) {
        this.storageService = storageService;
        this.uploadPolicy = new UploadPolicy(uploadLimits);
    }

    /** 上传文件。 */
    public FileVO upload(MultipartFile file) {
        uploadPolicy.validate(file.getOriginalFilename(), file.getContentType(), file.getSize());
        try {
            var bytes = file.getBytes();
            var key =
                    storageService.upload(
                            new ByteArrayInputStream(bytes),
                            file.getOriginalFilename(),
                            file.getContentType());
            return toFileVO(
                    key, file.getOriginalFilename(), bytes.length, file.getContentType(), bytes);
        } catch (IOException e) {
            throw new StorageException("文件上传失败", e);
        }
    }

    /** 删除文件。 */
    public void delete(String key) {
        storageService.delete(key);
    }

    /** 获取文件访问 URL。 */
    public String getUrl(String key) {
        return storageService.getUrl(key);
    }

    /** 从远程 URL 下载并上传，返回物理文件元数据。 */
    public FileVO uploadFromUrl(String url, String path, String contentType) {
        try {
            var conn = (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.connect();
            var maxRedirects = 5;
            while (maxRedirects-- > 0) {
                var code = conn.getResponseCode();
                if (code == java.net.HttpURLConnection.HTTP_MOVED_PERM
                        || code == java.net.HttpURLConnection.HTTP_MOVED_TEMP
                        || code == 307
                        || code == 308) {
                    var location = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn =
                            (java.net.HttpURLConnection)
                                    URI.create(location).toURL().openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(10_000);
                    conn.setReadTimeout(30_000);
                    conn.connect();
                } else {
                    break;
                }
            }
            try (var input = conn.getInputStream()) {
                var bytes = uploadPolicy.readWithLimit(input);
                uploadPolicy.validate(path, contentType, bytes.length);
                var key = storageService.upload(new ByteArrayInputStream(bytes), path, contentType);
                return toFileVO(key, path, bytes.length, contentType, bytes);
            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            log.error("[FileService] uploadFromUrl 失败: url={}, path={}", url, path, e);
            throw new StorageException("从 URL 上传文件失败: " + url, e);
        }
    }

    /** 上传字节数组，返回物理文件元数据。 */
    public FileVO uploadFromBytes(byte[] bytes, String path, String contentType) {
        uploadPolicy.validate(path, contentType, bytes != null ? bytes.length : 0);
        try {
            var key = storageService.upload(new ByteArrayInputStream(bytes), path, contentType);
            return toFileVO(key, path, bytes.length, contentType, bytes);
        } catch (Exception e) {
            throw new StorageException("字节数组上传文件失败: path=" + path, e);
        }
    }

    /** 上传 Base64 内容，返回物理文件元数据。 */
    public FileVO uploadFromBase64(String b64, String path) {
        var mime = "application/octet-stream";
        var data = b64;
        if (b64 != null && b64.startsWith("data:")) {
            var comma = b64.indexOf(',');
            if (comma > 0) {
                var header = b64.substring(5, comma);
                mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
                data = b64.substring(comma + 1);
            }
        }
        var bytes = Base64.getDecoder().decode(data);
        if ("application/octet-stream".equals(mime)) {
            mime = inferImageContentType(bytes);
        }
        uploadPolicy.validate(path, mime, bytes.length);
        try {
            var key = storageService.upload(new ByteArrayInputStream(bytes), path, mime);
            return toFileVO(key, path, bytes.length, mime, bytes);
        } catch (Exception e) {
            throw new StorageException("base64 上传文件失败: path=" + path, e);
        }
    }

    /** 根据受信任的图片文件签名识别无 data URL 前缀的 Base64 内容。 */
    private String inferImageContentType(byte[] bytes) {
        if (bytes == null) return "application/octet-stream";
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xff
                && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 6
                && bytes[0] == 0x47
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x38
                && (bytes[4] == 0x37 || bytes[4] == 0x39)
                && bytes[5] == 0x61) {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    /** 为已上传的视频生成 OSS 截帧缩略图。 */
    public String generateVideoThumbnail(String videoUrl) {
        if (!(storageService instanceof OssStorageService oss)) {
            return null;
        }
        var key = oss.urlToKey(videoUrl);
        if (key == null) return null;
        var thumbKey = oss.generateVideoThumbnail(key);
        return thumbKey != null ? oss.getUrl(thumbKey) : null;
    }

    private FileVO toFileVO(
            String key, String filename, long size, String contentType, byte[] bytes) {
        return new FileVO(
                key, storageService.getUrl(key), filename, size, contentType, sha256(bytes));
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境不支持 SHA-256", e);
        }
    }
}
