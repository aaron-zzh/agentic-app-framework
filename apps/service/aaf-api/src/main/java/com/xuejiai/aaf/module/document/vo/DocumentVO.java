package com.xuejiai.aaf.module.document.vo;

import java.time.LocalDateTime;
import java.util.Map;

import com.xuejiai.aaf.module.document.domain.Document;

/** 文档详情响应 VO。 */
public record DocumentVO(
        Long id,
        String title,
        String filePath,
        String content,
        Map<String, Object> frontMatter,
        String docType,
        String status,
        String publish,
        Long sourceFileId,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public static DocumentVO from(Document entity) {
        return new DocumentVO(
                entity.getId(),
                entity.getTitle(),
                entity.getFilePath(),
                entity.getContent(),
                entity.getFrontMatter(),
                entity.getDocType(),
                entity.getStatus(),
                entity.getPublish(),
                entity.getSourceFileId(),
                entity.getCreateTime(),
                entity.getUpdateTime());
    }
}
