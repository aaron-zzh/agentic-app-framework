package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

/** ObjectReadiness 的显式生成范围；未列出的 OPTIONAL 对象不进入范围。 */
public record AigcGenerationScope(List<Long> includedOptionalObjectIds) {

    public static final AigcGenerationScope REQUIRED_ONLY = new AigcGenerationScope(List.of());

    public AigcGenerationScope {
        includedOptionalObjectIds =
                includedOptionalObjectIds == null
                        ? List.of()
                        : List.copyOf(includedOptionalObjectIds);
    }
}
