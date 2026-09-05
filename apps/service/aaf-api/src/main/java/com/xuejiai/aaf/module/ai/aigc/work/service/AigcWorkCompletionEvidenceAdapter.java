package com.xuejiai.aaf.module.ai.aigc.work.service;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.aigc.project.api.CompletionEvidencePort;
import com.xuejiai.aaf.module.ai.aigc.work.repository.AigcWorkPublicationRepository;
import com.xuejiai.aaf.module.ai.aigc.work.repository.AigcWorkRepository;

import lombok.RequiredArgsConstructor;

/** Work 域为 Project 完成命令提供只读交付证据。 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcWorkCompletionEvidenceAdapter implements CompletionEvidencePort {

    private static final Set<String> ACTIVE_PUBLICATION_STATUSES =
            Set.of("PENDING", "SCHEDULED", "PUBLISHING");

    private final AigcWorkRepository workRepository;
    private final AigcWorkPublicationRepository publicationRepository;

    @Override
    public CompletionEvidence load(Long projectId) {
        var activeWorks = workRepository.findByProjectIdAndStatusNot(projectId, "ARCHIVED");
        if (activeWorks.isEmpty()) {
            return new CompletionEvidence(0, 0, 0, java.util.List.of());
        }
        var workIds = activeWorks.stream().map(work -> work.getId()).toList();
        var publications = publicationRepository.findByWorkIdIn(workIds);
        var activeCount = publications.stream()
                .filter(publication -> ACTIVE_PUBLICATION_STATUSES.contains(publication.getStatus()))
                .count();
        var succeededChannels = publications.stream()
                .filter(publication -> "SUCCEEDED".equals(publication.getStatus()))
                .map(publication -> publication.getChannelSpecVersionId())
                .distinct()
                .sorted()
                .toList();
        return new CompletionEvidence(
                activeWorks.size(), publications.size(), activeCount, succeededChannels);
    }
}
