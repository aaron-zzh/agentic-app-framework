package com.xuejiai.aaf.framework.engine.prompt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** 将代码仓库中的 Prompt Markdown 显式同步为数据库版本；运行时仍只读取 currentVersion。 */
@Component
@RequiredArgsConstructor
public class PromptMarkdownSourceSynchronizer implements ApplicationRunner {

    private static final String SOURCE_PATTERN = "classpath*:aaf/prompt-sources/*.md";

    private final PromptMarkdownSourceParser parser;
    private final PromptTemplateRepository templateRepository;
    private final PromptTemplateVersionRepository versionRepository;
    private final PromptPublicationService publicationService;

    @Value("${aaf.prompt.source-sync.mode:DISABLED}")
    private String mode;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var synchronizationMode = SynchronizationMode.parse(mode);
        if (synchronizationMode == SynchronizationMode.DISABLED) return;
        try {
            var resources = new PathMatchingResourcePatternResolver().getResources(SOURCE_PATTERN);
            for (var resource : resources) synchronize(parser.parse(resource), synchronizationMode);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Prompt Markdown 源文件扫描失败", exception);
        }
    }

    @Transactional
    void synchronize(
            PromptMarkdownSourceParser.PromptSource source,
            SynchronizationMode synchronizationMode) {
        var template =
                templateRepository
                        .findByCodeForUpdate(source.code())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Prompt 源缺少 v12 seed 根对象: " + source.code()));
        var existing =
                versionRepository.findByTemplateCodeAndTemplateVersionAndDeletedFalse(
                        source.code(), source.version());
        if (existing.isPresent()) {
            var existingVersion = existing.orElseThrow();
            if (!source.contentHash().equals(existingVersion.getContentHash())) {
                throw new IllegalStateException(
                        "不可变 Prompt 版本内容不匹配，必须递增 MD version: "
                                + source.code()
                                + "@"
                                + source.version());
            }
            if (synchronizationMode == SynchronizationMode.DEV_PUBLISH
                    && existingVersion.getStatus() == PromptVersionStatus.DRAFT) {
                publicationService.publishDraft(source.code(), source.version());
            }
            return;
        }
        var latest =
                versionRepository.findTopByTemplateCodeAndDeletedFalseOrderByTemplateVersionDesc(
                        source.code());
        if (latest.isPresent()
                && source.version() != latest.orElseThrow().getTemplateVersion() + 1) {
            throw new IllegalStateException("Prompt 源版本必须为既有最新版本加一: " + source.code());
        }
        var version = new PromptTemplateVersion();
        version.setTemplate(template);
        version.setTemplateVersion(source.version());
        version.setStatus(PromptVersionStatus.DRAFT);
        version.setContent(source.content());
        version.setVariables("[]");
        version.setContentHash(source.contentHash());
        version.setChangeSummary(source.changeSummary());
        versionRepository.save(version);
        if (synchronizationMode == SynchronizationMode.DEV_PUBLISH) {
            publicationService.publishDraft(source.code(), source.version());
        }
    }

    enum SynchronizationMode {
        DISABLED,
        DEV_PUBLISH,
        PROD_DRAFT;

        static SynchronizationMode parse(String value) {
            try {
                return value == null
                        ? DISABLED
                        : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "不支持的 Prompt source-sync mode: " + value, exception);
            }
        }
    }
}
