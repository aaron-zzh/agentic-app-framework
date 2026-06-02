/**
 * Prompt 引擎默认实现。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient.LlmMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Prompt 引擎实现：存储/版本/渲染/链式组装/评估。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultPromptEngine implements PromptEngine {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private final PromptTemplateRepository repository;
    private final LlmClient llmClient;

    // ─── 存储与版本 ───

    @Override
    @Transactional
    public PromptTemplate create(PromptTemplate template) {
        template.setVersion(1);
        template.setActive(true);
        return repository.save(template);
    }

    @Override
    @Transactional
    public PromptTemplate createNewVersion(String name, String content) {
        var existing = repository.findByNameOrderByVersionDesc(name);
        int nextVersion = existing.isEmpty() ? 1 : existing.getFirst().getVersion() + 1;

        // 失活旧版本
        existing.stream()
                .filter(PromptTemplate::getActive)
                .forEach(
                        t -> {
                            t.setActive(false);
                            repository.save(t);
                        });

        var template = new PromptTemplate();
        template.setName(name);
        template.setContent(content);
        template.setVersion(nextVersion);
        template.setActive(true);
        if (!existing.isEmpty()) {
            template.setDescription(existing.getFirst().getDescription());
            template.setCategory(existing.getFirst().getCategory());
            template.setVariables(existing.getFirst().getVariables());
        }
        return repository.save(template);
    }

    @Override
    public Optional<PromptTemplate> findActive(String name) {
        return repository.findByNameAndActiveTrue(name);
    }

    @Override
    public Optional<PromptTemplate> findByVersion(String name, int version) {
        return repository.findByNameAndVersion(name, version);
    }

    @Override
    public List<PromptTemplate> findAllVersions(String name) {
        return repository.findByNameOrderByVersionDesc(name);
    }

    @Override
    public List<PromptTemplate> findByCategory(String category) {
        return repository.findByCategory(category);
    }

    // ─── 渲染与组装 ───

    @Override
    public String render(String templateName, Map<String, String> variables) {
        var template =
                repository
                        .findByNameAndActiveTrue(templateName)
                        .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateName));
        return interpolate(template.getContent(), variables);
    }

    @Override
    public String render(String templateName, int version, Map<String, String> variables) {
        var template =
                repository
                        .findByNameAndVersion(templateName, version)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "模板版本不存在: " + templateName + " v" + version));
        return interpolate(template.getContent(), variables);
    }

    @Override
    public String chain(List<String> templateNames, Map<String, String> variables) {
        var sb = new StringBuilder();
        for (var name : templateNames) {
            findActive(name)
                    .ifPresent(
                            t -> {
                                sb.append(interpolate(t.getContent(), variables));
                                sb.append("\n\n");
                            });
        }
        return sb.toString().trim();
    }

    @Override
    public String renderWithExamples(
            String templateName, Map<String, String> variables, int maxExamples) {
        // TODO: 从数据库加载关联的 Few-shot 示例，按相关度排序截取 topK
        return render(templateName, variables);
    }

    // ─── 评估 ───

    @Override
    public PromptEvalResult evaluate(String templateName, List<PromptEvalCase> testCases) {
        if (testCases == null || testCases.isEmpty()) {
            return new PromptEvalResult(templateName, 0.0, "无测试用例");
        }

        var template = repository.findByNameAndActiveTrue(templateName).orElse(null);
        if (template == null) {
            return new PromptEvalResult(templateName, 0.0, "模板不存在: " + templateName);
        }

        int passed = 0;
        var details = new StringBuilder();

        for (int i = 0; i < testCases.size(); i++) {
            var tc = testCases.get(i);
            var rendered = interpolate(template.getContent(), tc.variables());
            try {
                var actual =
                        llmClient.call(List.of(LlmMessage.user(rendered)), "prompt_eval", null);
                var similarity = computeSimilarity(actual, tc.expectedOutput());
                if (similarity >= 0.6) {
                    passed++;
                }
                details.append("#%d: %.2f ".formatted(i + 1, similarity));
            } catch (Exception e) {
                log.warn("[PromptEval] 用例 #{} 执行失败: {}", i + 1, e.getMessage());
                details.append("#%d: ERROR ".formatted(i + 1));
            }
        }

        double score = (double) passed / testCases.size();
        return new PromptEvalResult(
                templateName,
                score,
                "通过 %d/%d | %s".formatted(passed, testCases.size(), details.toString().trim()));
    }

    /** 简单相似度：基于公共子序列长度占比 */
    private double computeSimilarity(String actual, String expected) {
        if (expected == null || expected.isBlank()) return 1.0;
        if (actual == null || actual.isBlank()) return 0.0;
        // 关键词命中率
        var keywords = expected.split("[\\s,;，；。.]+");
        long hits = 0;
        for (var kw : keywords) {
            if (!kw.isBlank() && actual.contains(kw)) hits++;
        }
        return keywords.length > 0 ? (double) hits / keywords.length : 0.0;
    }

    // ─── 内部方法 ───

    private String interpolate(String content, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return content;
        }
        Matcher matcher = VAR_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
