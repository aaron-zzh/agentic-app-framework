package com.xuejiai.aaf.module.ai.aigc.work.service;

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

    private final AigcWorkRepository workRepository;
    private final AigcWorkPublicationRepository publicationRepository;

    @Override
    public CompletionEvidence load(Long projectId) {
        var activeWorks = workRepository.findByProjectIdAndStatusNot(projectId, "archived");
        if (activeWorks.isEmpty()) {
            return new CompletionEvidence(0, 0, 0);
        }
        var workIds = activeWorks.stream().map(work -> work.getId()).toList();
        var publications = publicationRepository.findByWorkIdIn(workIds);
        var unpublished =
                publications.stream()
                        .filter(publication -> !"published".equals(publication.getStatus()))
                        .count();
        return new CompletionEvidence(activeWorks.size(), publications.size(), unpublished);
    }
}
