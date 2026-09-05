package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AigcObjectVersionComparisonView(
        AigcObjectVersionComparisonItem left, AigcObjectVersionComparisonItem right) {

    public record AigcObjectVersionComparisonItem(
            Long objectVersionId,
            String status,
            Map<String, Object> content,
            Long documentVersionId,
            List<Long> mediaVersionIds,
            Long sourceExecutionRunId,
            Long creditCost,
            LocalDateTime createdTime) {

        public AigcObjectVersionComparisonItem {
            content =
                    content == null
                            ? Map.of()
                            : java.util.Collections.unmodifiableMap(
                                    new java.util.LinkedHashMap<>(content));
            mediaVersionIds = mediaVersionIds == null ? List.of() : List.copyOf(mediaVersionIds);
        }
    }
}
