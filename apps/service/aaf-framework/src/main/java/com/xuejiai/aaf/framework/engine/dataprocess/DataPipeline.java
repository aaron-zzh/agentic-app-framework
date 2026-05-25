package com.xuejiai.aaf.framework.engine.dataprocess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据处理管道——串联各 ProcessingStep 执行。
 *
 * <p>流程：原始数据 → FieldMapper → DataCleaner → AiEnricher → DataRouter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPipeline implements DataProcessEngine {

    private final List<ProcessingStep> steps;

    @Override
    public ProcessResult process(ProcessRequest request) {
        // 简单委托，兼容旧接口
        return new ProcessResult(true, request.input(), null);
    }

    /**
     * 执行完整管道。
     *
     * @param items 原始数据列表
     * @param config 管道配置
     * @return 处理上下文（含结果和日志）
     */
    public ProcessingContext execute(List<Map<String, Object>> items, PipelineConfig config) {
        var context = new ProcessingContext(new ArrayList<>(items), config);
        log.info("管道启动 [{}] 输入 {} 条数据", config.getPipelineId(), items.size());

        for (var step : steps) {
            if (context.isAborted()) {
                context.log(step.name(), "管道已中止，跳过");
                break;
            }
            try {
                context = step.execute(context);
                context.log(step.name(), "完成，剩余 %d 条".formatted(context.itemCount()));
            } catch (Exception e) {
                log.error("[{}] 步骤 {} 失败: {}", config.getPipelineId(), step.name(), e.getMessage());
                context.log(step.name(), "失败: " + e.getMessage());
                context.setAborted(true);
            }
        }

        log.info("管道完成 [{}] 输出 {} 条数据", config.getPipelineId(), context.itemCount());
        return context;
    }
}
