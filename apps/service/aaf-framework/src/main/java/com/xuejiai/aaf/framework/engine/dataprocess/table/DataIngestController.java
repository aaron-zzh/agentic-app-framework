package com.xuejiai.aaf.framework.engine.dataprocess.table;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.framework.engine.dataprocess.DataPipeline;
import com.xuejiai.aaf.framework.engine.dataprocess.PipelineConfig;
import com.xuejiai.aaf.framework.security.apikey.ApiKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据摄入 API——外部服务通过 API Key 推送数据，经 Pipeline 处理后入库。
 *
 * <p>鉴权由全局 ApiKeyAuthFilter 处理，此处只做 scope 检查。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class DataIngestController {

    private final DataPipeline pipeline;
    private final DynamicTableService tableService;

    /**
     * 数据摄入（带 Pipeline 处理）。
     *
     * <p>认证方式：{@code Authorization: Bearer aaf_dk_xxx} 或 {@code X-API-Key: aaf_dk_xxx}
     */
    @PostMapping("/{tableSlug}")
    public Map<String, Object> ingest(
            @PathVariable String tableSlug, @RequestBody List<Map<String, Object>> items) {

        // scope 检查
        var apiKey = getApiKeyFromContext();
        if (apiKey != null && !apiKey.hasScope("ingest")) {
            return Map.of("error", "API Key 无 ingest 权限");
        }
        if (apiKey != null && !apiKey.canAccessTable(tableSlug)) {
            return Map.of("error", "API Key 无权访问表: " + tableSlug);
        }

        // 确认表存在
        tableService.getTable(tableSlug);

        // 执行 Pipeline
        var config =
                PipelineConfig.builder()
                        .pipelineId("ingest:" + tableSlug + ":" + System.currentTimeMillis())
                        .routeTarget(
                                PipelineConfig.RouteTarget.builder()
                                        .type("custom_table")
                                        .target(tableSlug)
                                        .build())
                        .build();

        var context = pipeline.execute(items, config);
        var inserted = context.getMetadata().getOrDefault("inserted_count", 0);

        log.info("数据摄入完成 [{}] items={} inserted={}", tableSlug, items.size(), inserted);

        return Map.of(
                "status",
                context.isAborted() ? "partial" : "completed",
                "input_count",
                items.size(),
                "inserted_count",
                inserted,
                "logs",
                context.getLogs());
    }

    private ApiKey getApiKeyFromContext() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof ApiKey key) {
            return key;
        }
        return null;
    }
}
