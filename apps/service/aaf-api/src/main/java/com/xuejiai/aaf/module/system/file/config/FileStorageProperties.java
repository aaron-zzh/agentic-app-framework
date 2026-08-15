package com.xuejiai.aaf.module.system.file.config;

import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.xuejiai.aaf.framework.storage.UploadLimits;

/** 文件存储环境属性；数据库保存规格，YAML/ENV 只保存真实凭证与上传限制。 */
@ConfigurationProperties(prefix = "aaf.storage")
public record FileStorageProperties(
        Map<String, CredentialProperties> credentials, UploadProperties upload) {

    public record CredentialProperties(String accessKeyId, String accessKeySecret) {}

    public record UploadProperties(Set<String> allowedContentTypes, Long maxSizeBytes) {}

    public UploadLimits uploadLimits() {
        if (upload == null) {
            return UploadLimits.defaults();
        }
        var defaults = UploadLimits.defaults();
        return new UploadLimits(
                upload.allowedContentTypes() == null
                        ? defaults.allowedContentTypes()
                        : Set.copyOf(upload.allowedContentTypes()),
                upload.maxSizeBytes() == null ? defaults.maxSizeBytes() : upload.maxSizeBytes());
    }
}
