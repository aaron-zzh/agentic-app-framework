package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.List;

public record AigcExecutionRunTreeView(
        AigcExecutionRunView root, List<AigcExecutionRunView> descendants) {

    public AigcExecutionRunTreeView {
        descendants = descendants == null ? List.of() : List.copyOf(descendants);
    }
}
