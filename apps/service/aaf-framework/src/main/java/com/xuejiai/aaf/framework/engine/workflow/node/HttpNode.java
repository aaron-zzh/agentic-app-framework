package com.xuejiai.aaf.framework.engine.workflow.node;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTP 请求节点——工作流中调用外部 API。
 *
 * <p>流程变量：url（必填）、method（GET/POST，默认GET）、body（POST 时）、headers（JSON 字符串，可选）、output/statusCode（节点写入）
 */
@Slf4j
@Component("httpNode")
public class HttpNode implements JavaDelegate {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Override
    public void execute(DelegateExecution execution) {
        var url = (String) execution.getVariable("url");
        var method = execution.getVariable("method") != null
                ? (String) execution.getVariable("method") : "GET";
        var body = (String) execution.getVariable("body");

        try {
            var builder = HttpRequest.newBuilder().uri(URI.create(url));
            if ("POST".equalsIgnoreCase(method) && body != null) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json");
            } else {
                builder.GET();
            }

            var response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            execution.setVariable("output", response.body());
            execution.setVariable("statusCode", response.statusCode());
            execution.setVariable("success", response.statusCode() < 400);
        } catch (Exception e) {
            log.error("HttpNode 请求失败: url={}", url, e);
            execution.setVariable("success", false);
            execution.setVariable("error", e.getMessage());
        }
    }
}
