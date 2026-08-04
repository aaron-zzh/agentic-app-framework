package com.xuejiai.aaf.module.ai.aigc.media.api;

import java.math.BigDecimal;

import com.xuejiai.aaf.module.system.file.api.StoredFile;

/** 生成链完成物理文件持久化后创建媒体及首个不可变版本的命令。 */
public record AigcGeneratedMediaCommand(
        Long userId,
        String name,
        AigcMediaType mediaType,
        StoredFile file,
        StoredFile thumbnailFile,
        Long sourceExecutionRunId,
        Long sourceTaskId,
        Long originalProjectId,
        Integer width,
        Integer height,
        BigDecimal duration,
        BigDecimal frameRate,
        String generationInfo) {}
