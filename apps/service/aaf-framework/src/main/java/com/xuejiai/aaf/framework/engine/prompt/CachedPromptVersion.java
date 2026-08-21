package com.xuejiai.aaf.framework.engine.prompt;

/** 已发布 Prompt 的缓存快照；不包含 JPA 延迟对象或执行期插值结果。 */
public record CachedPromptVersion(
        String code,
        PromptKind kind,
        PromptVisibility visibility,
        int templateVersion,
        String content,
        String negativePrompt,
        String variables,
        String contentHash) {}
