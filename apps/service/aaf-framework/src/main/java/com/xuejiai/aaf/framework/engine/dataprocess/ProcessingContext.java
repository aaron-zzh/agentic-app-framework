package com.xuejiai.aaf.framework.engine.dataprocess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** 数据处理上下文——在 Pipeline 各步骤间传递数据和状态。 */
@Getter
@Setter
public class ProcessingContext {

    /** 待处理的数据项列表（每项是一个 Map） */
    private List<Map<String, Object>> items;

    /** 管道配置 */
    private PipelineConfig config;

    /** 处理过程中的元数据（步骤间共享） */
    private Map<String, Object> metadata = new HashMap<>();

    /** 处理日志 */
    private List<String> logs = new ArrayList<>();

    /** 是否中止后续步骤 */
    private boolean aborted = false;

    public ProcessingContext(List<Map<String, Object>> items, PipelineConfig config) {
        this.items = items;
        this.config = config;
    }

    public void log(String step, String message) {
        logs.add("[%s] %s".formatted(step, message));
    }

    public int itemCount() {
        return items != null ? items.size() : 0;
    }
}
