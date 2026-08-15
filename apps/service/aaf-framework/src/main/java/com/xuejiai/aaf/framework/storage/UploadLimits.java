package com.xuejiai.aaf.framework.storage;

import java.util.Set;

/** 文件上传类型与大小限制。 */
public record UploadLimits(Set<String> allowedContentTypes, long maxSizeBytes) {

    public static UploadLimits defaults() {
        return new UploadLimits(
                Set.of(
                        "image/jpeg",
                        "image/png",
                        "image/gif",
                        "image/webp",
                        "video/mp4",
                        "audio/mpeg",
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "text/plain",
                        "text/csv",
                        "text/markdown"),
                10L * 1024 * 1024);
    }
}
