package com.xuejiai.aaf.module.knowledge.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识文档只读引用查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识文档只读引用查询")
public class KnowledgeDocumentReferencePageDTO extends PageParam {

    private Long knowledgeBaseId;
    private String title;
    private Integer status;
}
