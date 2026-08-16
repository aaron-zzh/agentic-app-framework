package com.xuejiai.aaf.module.ai.aigc.copywriting.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 文案资产聚合分页结果。 */
public record CopywritingAssetPageVO(
        List<Asset> list, long total, int pageNo, int pageSize, boolean hasMore) {

    public CopywritingAssetPageVO {
        list = List.copyOf(list);
    }

    public record Asset(
            Long documentId,
            String title,
            String summary,
            LocalDateTime updateTime,
            List<Project> projects) {

        public Asset {
            projects = List.copyOf(projects);
        }
    }

    public record Project(Long projectId, String projectName) {}
}
