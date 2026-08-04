package com.xuejiai.aaf.module.ai.aigc.work.vo;

import java.time.LocalDateTime;
import java.util.Map;

public record AigcWorkPublicationVO(
        Long id,
        Integer version,
        Long workId,
        Long channelSpecVersionId,
        String channelCode,
        String externalId,
        String externalUrl,
        String status,
        LocalDateTime scheduledTime,
        LocalDateTime publishedTime,
        Map<String, Object> responsePayload,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
