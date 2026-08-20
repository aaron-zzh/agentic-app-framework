/**
 * Prompt 模板服务——Core 层调用门面，委托 engine/prompt 引擎执行。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.prompt.PromptEngine;
import com.xuejiai.aaf.framework.engine.prompt.PromptTemplate;

import lombok.RequiredArgsConstructor;

/** Core 层 Prompt 门面：供 Agent/Assistant/Cognition 调用，内部委托 PromptEngine。 */
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final PromptEngine promptEngine;

    /** 渲染模板：将变量注入到模板内容中 */
    public String render(String templateName, Map<String, String> variables) {
        return promptEngine.render(templateName, variables);
    }

    /** 按指定版本渲染 */
    public String render(String templateName, int version, Map<String, String> variables) {
        return promptEngine.render(templateName, version, variables);
    }

    /** 链式组装：将多个模板片段按顺序拼接 */
    public String chain(List<String> templateNames, Map<String, String> variables) {
        return promptEngine.chain(templateNames, variables);
    }

    /** 渲染并注入 Few-shot 示例（底层尚未实现，调用会抛 {@link UnsupportedOperationException}） */
    public String renderWithExamples(
            String templateName, Map<String, String> variables, int maxExamples) {
        return promptEngine.renderWithExamples(templateName, variables, maxExamples);
    }

    /** 要求同名 ENGINE Prompt 恰好存在一个 active 版本。 */
    public ResolvedPromptTemplate requireActive(String name) {
        var active =
                promptEngine.findAllVersions(name).stream()
                        .filter(template -> Boolean.TRUE.equals(template.getActive()))
                        .toList();
        if (active.size() != 1) {
            throw new IllegalStateException(
                    "ENGINE Prompt 必须恰好有一个 active 版本: %s，实际=%d".formatted(name, active.size()));
        }
        return resolve(active.getFirst());
    }

    /** 要求 ENGINE Prompt 的精确版本存在。 */
    public ResolvedPromptTemplate requireVersion(String name, int version) {
        return promptEngine
                .findByVersion(name, version)
                .map(PromptTemplateService::resolve)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "ENGINE Prompt 精确版本不存在: %s@%d".formatted(name, version)));
    }

    private static ResolvedPromptTemplate resolve(PromptTemplate template) {
        var content = template.getContent();
        return new ResolvedPromptTemplate(
                template.getName(),
                template.getTemplateVersion(),
                content,
                com.xuejiai.aaf.framework.engine.prompt.EnginePromptRegistration.sha256(content));
    }
}
