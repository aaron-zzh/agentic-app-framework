package com.xuejiai.aaf.module.ai.aigc.media.api;

import java.math.BigDecimal;

import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaType;
import com.xuejiai.aaf.module.system.file.api.StoredFile;

/** 生成链完成物理文件持久化后创建 Media+MediaVersion 的命令。 */
public record GeneratedMediaCommand(
        Long userId,
        String name,
        MediaType mediaType,
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
