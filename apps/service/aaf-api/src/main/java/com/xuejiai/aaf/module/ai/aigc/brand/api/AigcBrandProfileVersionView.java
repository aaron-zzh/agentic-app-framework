package com.xuejiai.aaf.module.ai.aigc.brand.api;

import java.util.List;

/** 已发布品牌资料版本的跨模块只读视图。 */
public record AigcBrandProfileVersionView(
        Long profileId,
        Long versionId,
        Integer versionNo,
        String profileType,
        String rulesJson,
        List<Long> mediaVersionIds,
        List<Long> documentVersionIds) {}
