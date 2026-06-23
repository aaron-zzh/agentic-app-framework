package com.xuejiai.aaf.module.ai.aigc.avatar.vo;

import java.time.LocalDateTime;

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
