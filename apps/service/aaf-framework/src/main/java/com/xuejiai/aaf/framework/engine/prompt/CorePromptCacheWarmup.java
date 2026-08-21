package com.xuejiai.aaf.framework.engine.prompt;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 启动时预热运行必需的已发布 Prompt；缺失或 hash 异常时拒绝启动。 */
@Component
@RequiredArgsConstructor
public class CorePromptCacheWarmup implements ApplicationRunner, Ordered {

    private static final List<String> REQUIRED_CODES =
            List.of(
                    "aaf.harness.constitution",
                    "aaf.context.summary",
                    "aaf.knowledge.fact-extraction.system",
                    "aaf.knowledge.fact-extraction.user",
                    "aaf.knowledge.entity-resolution.system",
                    "aaf.knowledge.entity-resolution.user");

    private final PromptVersionCache promptCache;

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        REQUIRED_CODES.forEach(promptCache::requireActive);
    }
}
