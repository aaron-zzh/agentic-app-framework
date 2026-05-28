/**
 * Prompt 模板服务——Core 层调用门面，委托 engine/prompt 引擎执行。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** 渲染并注入 Few-shot 示例 */
    public String renderWithExamples(
            String templateName, Map<String, String> variables, int maxExamples) {
        return promptEngine.renderWithExamples(templateName, variables, maxExamples);
    }

    /** 查找当前激活版本 */
    public Optional<PromptTemplate> findActive(String name) {
        return promptEngine.findActive(name);
    }
}
