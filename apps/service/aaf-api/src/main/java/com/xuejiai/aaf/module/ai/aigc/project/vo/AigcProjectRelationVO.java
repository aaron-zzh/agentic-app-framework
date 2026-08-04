package com.xuejiai.aaf.module.ai.aigc.project.vo;

import java.util.Map;

public record AigcProjectRelationVO(
        Long id,
        Long projectId,
        String relationType,
        String layer,
        Long sourceObjectId,
        Long targetObjectId,
        Map<String, Object> relationMeta) {}
