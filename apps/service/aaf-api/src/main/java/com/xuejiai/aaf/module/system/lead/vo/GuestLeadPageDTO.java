package com.xuejiai.aaf.module.system.lead.vo;

import com.xuejiai.aaf.common.enums.lead.LeadChannelEnum;
import com.xuejiai.aaf.common.enums.lead.LeadStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 访客线索分页查询请求。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "访客线索分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class GuestLeadPageDTO extends PageParam {

    @Schema(description = "动作渠道")
    private LeadChannelEnum channel;

    @Schema(description = "处理状态")
    private LeadStatusEnum status;

    @Schema(description = "访客匿名 ID")
    private String anonymousId;

    @Schema(description = "邮箱（精确匹配）")
    private String email;

    @Schema(description = "关联 contact ID")
    private Long contactId;
}
