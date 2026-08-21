package com.xuejiai.aaf.framework.engine.prompt;

/** 已发布 Prompt 版本事件；只在事务提交后用于缓存更新与跨实例本地失效。 */
public record PromptVersionPublishedEvent(CachedPromptVersion version) {}
