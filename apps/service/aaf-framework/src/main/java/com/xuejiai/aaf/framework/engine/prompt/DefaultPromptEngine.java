package com.xuejiai.aaf.framework.engine.prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient.LlmMessage;
import com.xuejiai.aaf.framework.org.OrgIgnore;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Prompt 引擎实现：只管理明确标记为 ENGINE 的内部系统 Prompt。 */
@Slf4j
@Service
@RequiredArgsConstructor
@OrgIgnore
public class DefaultPromptEngine implements PromptEngine {

    private final PromptTemplateRepository repository;
    private final PromptTemplateCompiler compiler;
    private final LlmClient llmClient;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public PromptTemplate create(PromptTemplate template) {
        makeSystemGlobal(template);
        template.setTemplateVersion(1);
        template.setActive(true);
        template.setVariables(
                compiler.serializeDeclarations(
                        template.getContent(), template.getNegativePrompt(), null));
        return repository.save(template);
    }

    @Override
    @Transactional
    public PromptTemplate createNewVersion(String name, String content) {
        var existing =
                repository.findByNameAndVisibilityOrderByTemplateVersionDesc(
                        name, PromptTemplate.VISIBILITY_ENGINE);
        int nextVersion = existing.isEmpty() ? 1 : existing.getFirst().getTemplateVersion() + 1;
        repository.deactivateActiveEngineVersions(name);

        var template = new PromptTemplate();
        template.setName(name);
        template.setContent(content);
        template.setTemplateVersion(nextVersion);
        template.setActive(true);
        if (!existing.isEmpty()) {
            var latest = existing.getFirst();
            template.setDescription(latest.getDescription());
            template.setCategory(latest.getCategory());
            template.setNegativePrompt(latest.getNegativePrompt());
            template.setModel(latest.getModel());
        }
        makeSystemGlobal(template);
        template.setVariables(
                compiler.serializeDeclarations(content, template.getNegativePrompt(), null));
        return repository.save(template);
    }

    @Override
    @Transactional
    public PromptTemplate registerEngineVersion(EnginePromptRegistration registration) {
        Objects.requireNonNull(registration, "registration 不能为空");
        lockEngineName(registration.name());
        var versions =
                repository.findByNameAndVisibilityOrderByTemplateVersionDesc(
                        registration.name(), PromptTemplate.VISIBILITY_ENGINE);
        var active = versions.stream().filter(PromptTemplate::getActive).toList();
        if (active.size() > 1) {
            throw new IllegalStateException(
                    "ENGINE Prompt active 状态异常: %s，实际=%d"
                            .formatted(registration.name(), active.size()));
        }
        var sameVersion =
                versions.stream()
                        .filter(
                                template ->
                                        template.getTemplateVersion()
                                                == registration.templateVersion())
                        .toList();
        if (sameVersion.size() > 1) {
            throw new IllegalStateException(
                    "ENGINE Prompt 同名同版本记录重复: %s@%d"
                            .formatted(registration.name(), registration.templateVersion()));
        }

        final PromptTemplate target;
        if (sameVersion.isEmpty()) {
            target = new PromptTemplate();
            target.setName(registration.name());
            target.setTemplateVersion(registration.templateVersion());
            target.setContent(registration.content());
            target.setDescription("AAF 内建引擎 Prompt");
            target.setVariables("[]");
            target.setActive(false);
            target.setCategory(registration.category());
            target.setCoverUrl(null);
            target.setNegativePrompt(null);
            target.setModel(null);
            target.setWidth(null);
            target.setHeight(null);
            target.setSteps(null);
            target.setSeed(null);
            target.setRemark(null);
            makeSystemGlobal(target);
            repository.saveAndFlush(target);
        } else {
            target = sameVersion.getFirst();
            validateRegisteredTemplate(target, registration);
        }

        if (registration.activate()) {
            if (!active.isEmpty()
                    && active.getFirst().getTemplateVersion() > registration.templateVersion()) {
                throw new IllegalStateException(
                        "ENGINE Prompt active 版本高于当前二进制声明版本: %s active=%d declared=%d"
                                .formatted(
                                        registration.name(),
                                        active.getFirst().getTemplateVersion(),
                                        registration.templateVersion()));
            }
            if (active.isEmpty()
                    || active.getFirst().getTemplateVersion() != registration.templateVersion()) {
                repository.deactivateActiveEngineVersions(registration.name());
                var managedTarget =
                        repository
                                .findByNameAndTemplateVersionAndVisibility(
                                        registration.name(),
                                        registration.templateVersion(),
                                        PromptTemplate.VISIBILITY_ENGINE)
                                .orElseThrow(
                                        () -> new IllegalStateException("ENGINE Prompt 注册后精确版本丢失"));
                managedTarget.setActive(true);
                repository.saveAndFlush(managedTarget);
            }
        }

        var reloaded =
                repository.findByNameAndVisibilityOrderByTemplateVersionDesc(
                        registration.name(), PromptTemplate.VISIBILITY_ENGINE);
        var registered =
                reloaded.stream()
                        .filter(
                                template ->
                                        template.getTemplateVersion()
                                                == registration.templateVersion())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("ENGINE Prompt 注册后版本不存在"));
        validateRegisteredTemplate(registered, registration);
        if (registration.activate()) {
            var reloadedActive = reloaded.stream().filter(PromptTemplate::getActive).toList();
            if (reloadedActive.size() != 1
                    || reloadedActive.getFirst().getTemplateVersion()
                            != registration.templateVersion()) {
                throw new IllegalStateException("ENGINE Prompt 注册后 active 校验失败");
            }
        }
        return registered;
    }

    @Override
    public Optional<PromptTemplate> findActive(String name) {
        return repository.findByNameAndActiveTrueAndVisibility(
                name, PromptTemplate.VISIBILITY_ENGINE);
    }

    @Override
    public Optional<PromptTemplate> findByVersion(String name, int version) {
        return repository.findByNameAndTemplateVersionAndVisibility(
                name, version, PromptTemplate.VISIBILITY_ENGINE);
    }

    @Override
    public List<PromptTemplate> findAllVersions(String name) {
        return repository.findByNameAndVisibilityOrderByTemplateVersionDesc(
                name, PromptTemplate.VISIBILITY_ENGINE);
    }

    @Override
    public List<PromptTemplate> findByCategory(String category) {
        return repository.findByCategoryAndVisibility(category, PromptTemplate.VISIBILITY_ENGINE);
    }

    @Override
    public String render(String templateName, Map<String, String> variables) {
        var template =
                findActive(templateName)
                        .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateName));
        return compile(template, variables, false);
    }

    @Override
    public String render(String templateName, int version, Map<String, String> variables) {
        var template =
                findByVersion(templateName, version)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "模板版本不存在: " + templateName + " v" + version));
        return compile(template, variables, false);
    }

    @Override
    public String chain(List<String> templateNames, Map<String, String> variables) {
        var fragments =
                templateNames.stream()
                        .map(this::findActive)
                        .flatMap(Optional::stream)
                        .map(
                                template ->
                                        compile(
                                                template,
                                                variablesForTemplate(template, variables),
                                                false))
                        .toList();
        return String.join("\n\n", fragments);
    }

    @Override
    public String renderWithExamples(
            String templateName, Map<String, String> variables, int maxExamples) {
        throw new UnsupportedOperationException(
                "Few-shot 示例渲染尚未实现，请改用 render(templateName, variables)");
    }

    @Override
    public PromptEvalResult evaluate(String templateName, List<PromptEvalCase> testCases) {
        if (testCases == null || testCases.isEmpty()) {
            return new PromptEvalResult(templateName, 0.0, "无测试用例");
        }
        var template = findActive(templateName).orElse(null);
        if (template == null) {
            return new PromptEvalResult(templateName, 0.0, "模板不存在: " + templateName);
        }

        int passed = 0;
        var details = new StringBuilder();
        for (int index = 0; index < testCases.size(); index++) {
            var testCase = testCases.get(index);
            var rendered = compile(template, testCase.variables(), false);
            try {
                var actual =
                        llmClient.call(List.of(LlmMessage.user(rendered)), "prompt_eval", null);
                var similarity = computeSimilarity(actual, testCase.expectedOutput());
                if (similarity >= 0.6) {
                    passed++;
                }
                details.append("#%d: %.2f ".formatted(index + 1, similarity));
            } catch (Exception exception) {
                log.warn("[PromptEval] 用例 #{} 执行失败: {}", index + 1, exception.getMessage());
                details.append("#%d: ERROR ".formatted(index + 1));
            }
        }
        double score = (double) passed / testCases.size();
        return new PromptEvalResult(
                templateName,
                score,
                "通过 %d/%d | %s".formatted(passed, testCases.size(), details.toString().trim()));
    }

    private String compile(
            PromptTemplate template, Map<String, String> variables, boolean negativePrompt) {
        return compiler.compile(
                template.getContent(),
                template.getNegativePrompt(),
                template.getVariables(),
                variables,
                negativePrompt);
    }

    private Map<String, String> variablesForTemplate(
            PromptTemplate template, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }
        var selected = new LinkedHashMap<String, String>();
        compiler.readDeclarations(
                        template.getContent(),
                        template.getNegativePrompt(),
                        template.getVariables())
                .forEach(
                        name -> {
                            if (variables.containsKey(name)) {
                                selected.put(name, variables.get(name));
                            }
                        });
        return selected;
    }

    private void lockEngineName(String name) {
        entityManager
                .createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, name)
                .getSingleResult();
    }

    private void validateRegisteredTemplate(
            PromptTemplate template, EnginePromptRegistration registration) {
        var contentSha256 = EnginePromptRegistration.sha256(template.getContent());
        var exact =
                Objects.equals(template.getName(), registration.name())
                        && Objects.equals(
                                template.getTemplateVersion(), registration.templateVersion())
                        && Objects.equals(template.getContent(), registration.content())
                        && contentSha256.equals(registration.contentSha256())
                        && Objects.equals(template.getDescription(), "AAF 内建引擎 Prompt")
                        && Objects.equals(template.getVariables(), "[]")
                        && Objects.equals(template.getCategory(), registration.category())
                        && template.getCoverUrl() == null
                        && Objects.equals(template.getType(), "SYSTEM")
                        && template.getNegativePrompt() == null
                        && template.getModel() == null
                        && template.getWidth() == null
                        && template.getHeight() == null
                        && template.getSteps() == null
                        && template.getSeed() == null
                        && Objects.equals(
                                template.getVisibility(), PromptTemplate.VISIBILITY_ENGINE)
                        && Objects.equals(template.getScope(), "SYSTEM")
                        && template.getOwnerId() == null
                        && template.getOrgId() == null
                        && template.getWorkspaceId() == null
                        && template.getRemark() == null;
        if (!exact) {
            throw new IllegalStateException(
                    "ENGINE Prompt 内容或固定元数据漂移: %s@%d"
                            .formatted(registration.name(), registration.templateVersion()));
        }
    }

    private void makeSystemGlobal(PromptTemplate template) {
        template.setOwnerId(null);
        template.setOrgId(null);
        template.setWorkspaceId(null);
        template.setType("SYSTEM");
        template.setScope("SYSTEM");
        template.setVisibility(PromptTemplate.VISIBILITY_ENGINE);
    }

    private double computeSimilarity(String actual, String expected) {
        if (expected == null || expected.isBlank()) {
            return 1.0;
        }
        if (actual == null || actual.isBlank()) {
            return 0.0;
        }
        var keywords = expected.split("[\\s,;，；。.]+");
        long hits = 0;
        for (var keyword : keywords) {
            if (!keyword.isBlank() && actual.contains(keyword)) {
                hits++;
            }
        }
        return keywords.length > 0 ? (double) hits / keywords.length : 0.0;
    }
}
