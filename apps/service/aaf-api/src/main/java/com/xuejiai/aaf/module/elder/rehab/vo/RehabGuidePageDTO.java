package com.xuejiai.aaf.module.elder.rehab.vo;

import com.xuejiai.aaf.common.enums.elder.RehabGuideCategoryEnum;
import com.xuejiai.aaf.common.enums.elder.RehabGuideStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.validation.InEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 康复指导分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "康复指导分页查询")
public class RehabGuidePageDTO extends PageParam {

    private String title;

    @InEnum(value = RehabGuideCategoryEnum.class, message = "康复指导分类必须是 {value}")
    private String category;

    @InEnum(value = RehabGuideStatusEnum.class, message = "康复指导状态必须是 {value}")
    private String status;

    private Long knowledgeDocumentId;
}
