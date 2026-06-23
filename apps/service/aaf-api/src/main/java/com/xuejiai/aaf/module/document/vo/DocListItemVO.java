package com.xuejiai.aaf.module.document.vo;

import java.time.LocalDateTime;

/** 文档列表条目（不含正文，用于前端列表展示）。 */
public record DocListItemVO(
        Long id, String title, String docType, String publish, LocalDateTime updateTime) {}
