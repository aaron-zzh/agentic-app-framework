package com.xuejiai.aaf.framework.engine.prompt;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Prompt 发布服务：唯一允许移动运行时 currentVersion 的受治理入口。 */
@Service
@RequiredArgsConstructor
public class PromptPublicationService {

    private final PromptTemplateRepository templateRepository;
    private final PromptTemplateVersionRepository versionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PromptTemplateVersion publishDraft(String code, int version) {
        var template =
                templateRepository
                        .findByCodeForUpdate(code)
                        .orElseThrow(() -> new IllegalArgumentException("Prompt 根对象不存在: " + code));
        var draft =
                versionRepository
                        .findByTemplateCodeAndTemplateVersionAndStatusAndDeletedFalse(
                                code, version, PromptVersionStatus.DRAFT)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Prompt Draft 版本不存在: %s@%d"
                                                        .formatted(code, version)));
        var previous = template.getCurrentVersion();
        if (previous != null && !java.util.Objects.equals(previous.getId(), draft.getId())) {
            previous.setStatus(PromptVersionStatus.ARCHIVED);
            versionRepository.save(previous);
        }
        draft.setStatus(PromptVersionStatus.PUBLISHED);
        template.setCurrentVersion(draft);
        versionRepository.save(draft);
        templateRepository.save(template);
        eventPublisher.publishEvent(
                new PromptVersionPublishedEvent(
                        new CachedPromptVersion(
                                code,
                                template.getKind(),
                                template.getVisibility(),
                                version,
                                draft.getContent(),
                                draft.getNegativePrompt(),
                                draft.getVariables(),
                                draft.getContentHash())));
        return draft;
    }
}
