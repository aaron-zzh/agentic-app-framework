package com.xuejiai.aaf.framework.intelligent.core.prompt;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.prompt.CachedPromptVersion;
import com.xuejiai.aaf.framework.engine.prompt.PromptEngine;
import com.xuejiai.aaf.framework.engine.prompt.PromptVersionCache;

import lombok.RequiredArgsConstructor;

/** Core 层 Prompt 门面：运行时只解析缓存中的已发布不可变版本。 */
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final PromptEngine promptEngine;
    private final PromptVersionCache promptCache;

    public String render(String code, Map<String, String> variables) {
        return promptEngine.render(code, variables);
    }

    public String render(String code, int version, Map<String, String> variables) {
        return promptEngine.render(code, version, variables);
    }

    public String chain(List<String> codes, Map<String, String> variables) {
        return promptEngine.chain(codes, variables);
    }

    public ResolvedPromptTemplate requireActive(String code) {
        return promptEngine
                .findActive(code)
                .map(PromptTemplateService::resolve)
                .orElseThrow(() -> new IllegalStateException("ENGINE Prompt 必须存在已发布版本: " + code));
    }

    public ResolvedPromptTemplate requirePublished(String code) {
        return resolve(promptCache.requireActive(code));
    }

    public ResolvedPromptTemplate requireVersion(String code, int version) {
        return promptEngine
                .findByVersion(code, version)
                .map(PromptTemplateService::resolve)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "ENGINE Prompt 精确版本不存在: %s@%d".formatted(code, version)));
    }

    private static ResolvedPromptTemplate resolve(CachedPromptVersion version) {
        return new ResolvedPromptTemplate(
                version.code(),
                version.templateVersion(),
                version.content(),
                version.contentHash());
    }
}
