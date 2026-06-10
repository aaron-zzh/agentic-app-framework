package com.xuejiai.aaf.module.chat.livechat.rating.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话评价分页查询 DTO。
 *
 * @author AaronZZH & Kiro
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RatingPageDTO extends PageParam {

    /** 按会话 ID 筛选 */
    private Long conversationId;

    /** 按客服 ID 筛选 */
    private Long staffId;

    /** 最低评分（含） */
    private Integer minScore;

    /** 最高评分（含） */
    private Integer maxScore;
}
