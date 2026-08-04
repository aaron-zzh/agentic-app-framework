package com.xuejiai.aaf.module.ai.aigc.brand.api;

import java.util.Collection;
import java.util.List;

/** 品牌资料版本跨子模块读取边界。 */
public interface AigcBrandApi {

    AigcBrandProfileVersionView requireVersion(Long brandProfileVersionId, Long workspaceId);

    List<AigcBrandProfileVersionView> requireVersions(
            Collection<Long> versionIds, Long workspaceId);
}
