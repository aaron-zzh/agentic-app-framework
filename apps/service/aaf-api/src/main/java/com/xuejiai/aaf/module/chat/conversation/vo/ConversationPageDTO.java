package com.xuejiai.aaf.module.chat.conversation.vo;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.module.chat.enums.ConversationStatus;
import com.xuejiai.aaf.module.chat.enums.ConversationType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话分页查询参数。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "会话分页查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationPageDTO extends PageParam {

    @Schema(description = "会话类型")
    private ConversationType type;

    @Schema(description = "状态")
    private ConversationStatus status;

    @Schema(description = "创建者 ID")
    private Long creatorId;

    @Schema(description = "助理 ID")
    private Long assistantId;

    @Schema(description = "标题模糊搜索")
    private String search;
}
