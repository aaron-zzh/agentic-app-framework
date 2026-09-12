package com.xuejiai.aaf.framework.intelligent.assistant.model.analysis;

import java.util.Objects;

/** 普通 Run 在执行前由确定性硬门得到的运行形态，不包含独立分类模型判断。 */
public record TaskAnalysis(Route route, String reason) {

    public TaskAnalysis {
        Objects.requireNonNull(route, "route 不能为空");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason 不能为空白");
        }
        reason = reason.trim();
    }

    public boolean persistent() {
        return route != Route.DIRECT;
    }

    public enum Route {
        DIRECT,
        TASK,
        TEAM
    }
}
