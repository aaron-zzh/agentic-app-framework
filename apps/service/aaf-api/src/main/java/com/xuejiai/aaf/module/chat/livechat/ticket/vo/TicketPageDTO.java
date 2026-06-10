package com.xuejiai.aaf.module.chat.livechat.ticket.vo;

import com.xuejiai.aaf.common.model.PageParam;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 工单分页查询参数。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Schema(description = "工单分页查询参数")
public class TicketPageDTO extends PageParam {

    @Schema(description = "工单状态")
    private String status;

    @Schema(description = "工单类型")
    private String type;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "受理客服 ID")
    private Long assigneeId;

    @Schema(description = "提交用户 ID")
    private Long userId;

    @Schema(description = "关联会话 ID")
    private Long conversationId;
}
