package com.xuejiai.aaf.module.system.task.action;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.system.task.domain.ScheduledTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook 动作——调用外部 HTTP 接口。 actionConfig JSON: {"url": "https://...", "method": "POST", "body":
 * "..."}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookActionExecutor implements ScheduledActionExecutor {

    private final RestClient.Builder restClientBuilder;

    @Override
    public String actionType() {
        return "WEBHOOK";
    }

    @Override
    public void execute(ScheduledTask task) {
        try {
            var config = JsonUtils.readTree(task.getActionConfig());
            var url = config.get("url").asString();
            var method = config.has("method") ? config.get("method").asString() : "POST";
            var body = config.has("body") ? config.get("body").asString("") : "";

            var client = restClientBuilder.build();
            var response =
                    "GET".equalsIgnoreCase(method)
                            ? client.get().uri(url).retrieve().toBodilessEntity()
                            : client.post().uri(url).body(body).retrieve().toBodilessEntity();

            log.info(
                    "Webhook 执行成功，taskId={}, url={}, status={}",
                    task.getId(),
                    url,
                    response.getStatusCode());
        } catch (Exception e) {
            log.error("WEBHOOK 动作执行失败，taskId={}", task.getId(), e);
            throw new RuntimeException("WEBHOOK 动作执行失败: " + e.getMessage(), e);
        }
    }
}
