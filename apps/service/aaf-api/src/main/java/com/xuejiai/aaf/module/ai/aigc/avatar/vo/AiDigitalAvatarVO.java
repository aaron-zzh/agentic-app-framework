package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiDigitalAvatarVO(
        Long id,
        String name,
        String imageUrl,
        Long sourceAssetId,
        String detectStatus,
        String detectReason,
        String defaultVoice,
        Long userId,
        LocalDateTime createTime) {}
