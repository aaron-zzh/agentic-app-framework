package com.xuejiai.aaf.framework.engine.workflow.trigger;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook 触发器——外部系统通过 HTTP 回调触发工作流执行。
 *
 * <p>URL 格式：POST /api/webhook/trigger/{processKey}
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook/trigger")
@RequiredArgsConstructor
public class WebhookTriggerController {

    private final WorkflowEngine workflowEngine;

    /**
     * 接收 Webhook 回调，启动指定工作流。
     *
     * @param processKey 工作流定义 key
     * @param payload 请求体作为工作流变量
     */
    @PostMapping("/{processKey}")
    public Map<String, Object> trigger(
            @PathVariable String processKey,
            @RequestBody(required = false) Map<String, Object> payload) {
        var variables = payload != null ? payload : Map.<String, Object>of();
        var instanceId = workflowEngine.startProcess(processKey, "webhook", variables);
        log.info("Webhook 触发: processKey={} instanceId={}", processKey, instanceId);
        return Map.of("processInstanceId", instanceId, "status", "started");
    }
}
