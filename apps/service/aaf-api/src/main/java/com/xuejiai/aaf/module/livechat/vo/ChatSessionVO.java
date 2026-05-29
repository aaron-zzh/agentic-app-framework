package com.xuejiai.aaf.module.livechat.vo;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.livechat.SessionStatusEnum;

/** 会话信息 VO。 */
public record ChatSessionVO(
        Long id,
        String externalUserId,
        ChannelTypeEnum channelType,
        SessionStatusEnum status,
        Long staffId,
        String skillGroup,
        String tags,
        Integer priority,
        LocalDateTime lastActiveTime,
        LocalDateTime createTime) {}
