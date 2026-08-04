package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.Map;

public record AigcProjectChannelRefVO(
        Long id, Long channelSpecId, Boolean primaryChannel, Map<String, Object> overrideConfig) {}
