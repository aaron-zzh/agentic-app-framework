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
        Long retryOfPublicationId,
        Integer retryCount,
        String failureCode,
        String failureMessage,
        LocalDateTime scheduledTime,
        LocalDateTime publishedTime,
        LocalDateTime canceledTime,
        String cancelReason,
        Map<String, Object> responsePayload,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
