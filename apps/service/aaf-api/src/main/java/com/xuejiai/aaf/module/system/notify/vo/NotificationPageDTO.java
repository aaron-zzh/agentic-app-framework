package com.xuejiai.aaf.module.system.notify.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 通知分页查询请求。 */
@Schema(description = "通知分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationPageDTO extends PageParam {

    @Schema(description = "通知类型")
    private String type;

    @Schema(description = "是否已读")
    private Boolean isRead;
}
