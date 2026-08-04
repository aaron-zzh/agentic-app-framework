package com.xuejiai.aaf.module.ai.aigc.copywriting.api;

import java.util.List;

import reactor.core.publisher.Flux;

/** 文案生成跨子模块接口。 */
public interface AigcCopywritingApi {
    Flux<String> generate(
            String modelId,
            String type,
            String prompt,
            String template,
            String length,
            String translateTo,
            List<String> referenceImageKeys);
}
