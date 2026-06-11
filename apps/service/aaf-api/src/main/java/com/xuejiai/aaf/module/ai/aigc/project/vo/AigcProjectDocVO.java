package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 项目关联文档响应 VO。 */
@Data
public class AigcProjectDocVO {
    private Long id;
    private Long projectId;
    private Long docId;
    private String docTitle;
    private String docType;

    /** 来源文件 ID（PDF 导入时非空） */
    private Long sourceFileId;

    private String role;
    private Integer sortOrder;
    private LocalDateTime createTime;
}
