package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

/** Project 完成前从 Work 域读取的不可变交付事实。 */
public interface CompletionEvidencePort {

    CompletionEvidence load(Long projectId);

    record CompletionEvidence(
            long activeWorkCount,
            long publicationCount,
            long activePublicationCount,
            List<Long> succeededChannelSpecVersionIds) {

        public CompletionEvidence {
            if (activeWorkCount < 0 || publicationCount < 0 || activePublicationCount < 0) {
                throw new IllegalArgumentException("CompletionEvidence 计数无效");
            }
            succeededChannelSpecVersionIds =
                    succeededChannelSpecVersionIds == null
                            ? List.of()
                            : succeededChannelSpecVersionIds.stream().distinct().sorted().toList();
        }

        public boolean hasActiveWork() {
            return activeWorkCount > 0;
        }
    }
}
