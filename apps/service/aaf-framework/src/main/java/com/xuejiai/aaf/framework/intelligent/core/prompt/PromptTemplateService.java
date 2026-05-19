/**
 * Prompt 模板服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Prompt 模板 CRUD、变量注入、版本管理。 */
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    private final PromptTemplateRepository repository;

    /** 渲染模板：将变量注入到模板内容中 */
    public String render(String templateName, Map<String, String> variables) {
        var template = repository.findByNameAndActiveTrue(templateName)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateName));
        return interpolate(template.getContent(), variables);
    }

    /** 按指定版本渲染 */
    public String render(String templateName, int version, Map<String, String> variables) {
        var template = repository.findByNameAndVersion(templateName, version)
                .orElseThrow(() -> new IllegalArgumentException("模板版本不存在: " + templateName + " v" + version));
        return interpolate(template.getContent(), variables);
    }

    /** 创建新模板 */
    @Transactional
    public PromptTemplate create(PromptTemplate template) {
        template.setVersion(1);
        template.setActive(true);
        return repository.save(template);
    }

    /** 创建新版本（旧版本自动失活） */
    @Transactional
    public PromptTemplate createNewVersion(String name, String content) {
        var existing = repository.findByNameOrderByVersionDesc(name);
        int nextVersion = existing.isEmpty() ? 1 : existing.getFirst().getVersion() + 1;

        // 失活旧版本
        existing.stream().filter(PromptTemplate::getActive).forEach(t -> {
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

    /** 查找当前激活版本 */
    public Optional<PromptTemplate> findActive(String name) {
        return repository.findByNameAndActiveTrue(name);
    }

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
