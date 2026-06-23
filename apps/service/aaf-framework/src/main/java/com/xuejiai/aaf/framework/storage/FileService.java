package com.xuejiai.aaf.framework.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;

import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 文件服务门面。在 StorageService 之上提供业务级文件操作。 */
@Slf4j
@RequiredArgsConstructor
public class FileService {

    private final StorageService storageService;
    private final StorageProperties.UploadLimits uploadLimits;

    /** 校验上传文件大小和类型 */
    private void validateUpload(MultipartFile file) {
        if (file.getSize() > uploadLimits.maxSizeBytes()) {
            throw new StorageException("文件超过大小限制", null);
        }
        if (!uploadLimits.allowedContentTypes().contains(file.getContentType())) {
            throw new StorageException("不允许的文件类型: " + file.getContentType(), null);
        }
    }

    /** 上传文件。 */
    public FileVO upload(MultipartFile file) {
        validateUpload(file);
        try {
            var key =
                    storageService.upload(
                            file.getInputStream(),
                            file.getOriginalFilename(),
                            file.getContentType());
            var url = storageService.getUrl(key);
            return new FileVO(
                    key, url, file.getOriginalFilename(), file.getSize(), file.getContentType());
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

    /**
     * 从远程 URL 下载文件并上传到存储，返回可访问 URL。
     *
     * @param url 远程文件 URL
     * @param path 存储路径（含文件名和扩展名），如 {@code aigc/image/xxx.png}
     * @param contentType MIME 类型
     * @return 存储后的可访问 URL
     */
    public String uploadFromUrl(String url, String path, String contentType) {
        try {
            // 跟随重定向（picsum 等服务会 302 跳转）
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.connect();
            // 手动处理最多 5 次重定向（HTTPS→HTTP 等跨协议情况 HttpURLConnection 不自动跟随）
            int maxRedirects = 5;
            while (maxRedirects-- > 0) {
                int code = conn.getResponseCode();
                if (code == java.net.HttpURLConnection.HTTP_MOVED_PERM
                        || code == java.net.HttpURLConnection.HTTP_MOVED_TEMP
                        || code == 307
                        || code == 308) {
                    String location = conn.getHeaderField("Location");
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
            try (var is = conn.getInputStream()) {
                String key = storageService.upload(is, path, contentType);
                return storageService.getUrl(key);
            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            log.error("[FileService] uploadFromUrl 失败: url={}, path={}", url, path, e);
            throw new StorageException("从 URL 上传文件失败: " + url, e);
        }
    }

    /**
     * 将字节数组上传到存储，返回可访问 URL。
     *
     * @param bytes 文件字节数组
     * @param path 存储路径（含文件名和扩展名），如 {@code aigc/voice/xxx.mp3}
     * @param contentType MIME 类型
     * @return 存储后的可访问 URL
     */
    public String uploadFromBytes(byte[] bytes, String path, String contentType) {
        try {
            String key = storageService.upload(new ByteArrayInputStream(bytes), path, contentType);
            return storageService.getUrl(key);
        } catch (Exception e) {
            throw new StorageException("字节数组上传文件失败: path=" + path, e);
        }
    }

    /**
     * 将 base64 字符串（支持 {@code data:mime;base64,} 前缀）解码后上传到存储，返回可访问 URL。
     *
     * @param b64 base64 字符串或 data URL
     * @param path 存储路径（含文件名和扩展名），如 {@code aigc/image/xxx.png}
     * @return 存储后的可访问 URL；若 {@code path} 中 MIME 类型不明确，可用 data URL 前缀推断
     */
    public String uploadFromBase64(String b64, String path) {
        String mime = "application/octet-stream";
        String data = b64;
        if (b64 != null && b64.startsWith("data:")) {
            int comma = b64.indexOf(',');
            if (comma > 0) {
                String header = b64.substring(5, comma);
                mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
                data = b64.substring(comma + 1);
            }
        }
        byte[] bytes = Base64.getDecoder().decode(data);
        try {
            String key = storageService.upload(new ByteArrayInputStream(bytes), path, mime);
            return storageService.getUrl(key);
        } catch (Exception e) {
            throw new StorageException("base64 上传文件失败: path=" + path, e);
        }
    }
}
