package com.xuejiai.aaf.module.ai.aigc.project.api;

/** Project 完成前从下游交付域读取证据的稳定边界。 */
public interface CompletionEvidencePort {

    CompletionEvidence load(Long projectId);

    record CompletionEvidence(
            long activeWorkCount, long publicationCount, long unpublishedPublicationCount) {

        public CompletionEvidence {
            if (activeWorkCount < 0
                    || publicationCount < 0
                    || unpublishedPublicationCount < 0
                    || unpublishedPublicationCount > publicationCount) {
                throw new IllegalArgumentException("CompletionEvidence 计数无效");
            }
        }

        public boolean hasActiveWork() {
            return activeWorkCount > 0;
        }

        public boolean publicationsCompleted() {
            return publicationCount == 0 || unpublishedPublicationCount == 0;
        }
    }
}
