package com.xuejiai.aaf.framework.engine.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Prompt 引擎：解析已发布不可变 ENGINE Prompt 快照并安全渲染变量。 */
public interface PromptEngine {

    Optional<CachedPromptVersion> findActive(String code);

    Optional<CachedPromptVersion> findByVersion(String code, int version);

    String render(String code, Map<String, String> variables);

    String render(String code, int version, Map<String, String> variables);

    String chain(List<String> codes, Map<String, String> variables);

    String renderWithExamples(String code, Map<String, String> variables, int maxExamples);
}
