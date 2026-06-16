package com.xuejiai.aaf.framework.storage;

import java.io.IOException;

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

    /** 上传图片。 */
    public FileVO uploadImage(MultipartFile file) {
        return upload(file);
    }

    /** 删除文件。 */
    public void delete(String key) {
        storageService.delete(key);
    }

    /** 获取文件访问 URL。 */
    public String getUrl(String key) {
        return storageService.getUrl(key);
    }
}
