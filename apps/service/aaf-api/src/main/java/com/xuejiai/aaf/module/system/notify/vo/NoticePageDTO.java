package com.xuejiai.aaf.module.system.notify.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知公告分页查询请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "通知公告分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticePageDTO extends PageParam {

    @Schema(description = "类型：NOTICE/ANNOUNCEMENT")
    private String type;

    @Schema(description = "状态：0=草稿 1=已发布")
    private Short status;
}
