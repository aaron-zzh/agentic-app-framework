package com.xuejiai.aaf.module.ai.aigc.media.api;

/** 将当前用户上传文件物化为持久媒体的命令。 */
public record AigcUploadedMediaCommand(
        Long userId, String name, AigcMediaType mediaType, Long fileId, Long originalProjectId) {}
