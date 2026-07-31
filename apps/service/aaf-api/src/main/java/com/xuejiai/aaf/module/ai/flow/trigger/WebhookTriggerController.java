package com.xuejiai.aaf.module.ai.flow.trigger;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.module.ai.flow.service.AiFlowTriggerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook 触发器——已认证外部系统通过 HTTP 回调触发 AI Flow 执行。
 *
 * <p>URL 格式：POST /api/webhook/trigger/{flowId}
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook/trigger")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WebhookTriggerController {

    private final AiFlowTriggerService triggerService;

    /**
     * 接收 Webhook 回调，启动指定 AI Flow。
     *
     * @param flowId AI Flow 业务 ID
     * @param payload 请求体作为流程变量
     */
    @PostMapping("/{flowId}")
    public Map<String, Object> trigger(
            @PathVariable Long flowId,
            @RequestBody(required = false) Map<String, Object> payload) {
        var identity = triggerService.currentIdentity();
        var result = triggerService.triggerWebhook(flowId, payload, identity);
        log.info(
                "Webhook 触发: flowId={} instanceId={} businessKey={}",
                result.flowId(),
                result.processInstanceId(),
                result.businessKey());
        return Map.of(
                "flowId", result.flowId(),
                "processInstanceId", result.processInstanceId(),
                "businessKey", result.businessKey(),
                "status", "started");
    }
}
