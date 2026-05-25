package com.xuejiai.aaf.framework.engine.tool.builtin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据采集工具——调用外部数据源 MCP 服务获取结构化数据。
 *
 * <p>风险等级：HIGH（涉及外部数据源访问）。
 * 通过 HTTP API 调用独立部署的数据采集服务，AAF 侧只负责调度和权限控制。
 */
@Slf4j
@Component
public class DataCollectorTool {

    @Value("${aaf.tools.data-collector.base-url:http://localhost:8100}")
    private String collectorBaseUrl;

    @Value("${aaf.tools.data-collector.timeout:60}")
    private int timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Tool(description = "从外部数据源采集社交媒体数据（抖音/小红书/B站/微博）。"
            + "支持搜索、用户内容、评论、详情等任务类型。"
            + "注意：此工具为高风险操作，每次调用需用户确认。")
    public String collect(
            @ToolParam(description = "目标平台：douyin/xiaohongshu/bilibili/weibo") String platform,
            @ToolParam(description = "任务类型：search/user_posts/comments/video_detail") String taskType,
            @ToolParam(description = "查询内容：搜索关键词、用户ID或内容ID") String query,
            @ToolParam(description = "返回数量上限", required = false) Integer limit) {
        try {
            var body = """
                    {"platform":"%s","task_type":"%s","query":"%s","limit":%d}"""
                    .formatted(platform, taskType, query, limit != null ? limit : 20);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(collectorBaseUrl + "/api/v1/collect"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }
            return "{\"error\":\"采集服务返回 %d: %s\"}".formatted(response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("采集服务调用失败: {}", e.getMessage());
            return "{\"error\":\"%s\"}".formatted(e.getMessage());
        }
    }

    @Tool(description = "查询数据采集任务状态（异步任务时使用）。")
    public String collectStatus(
            @ToolParam(description = "任务ID") String taskId) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(collectorBaseUrl + "/api/v1/collect/" + taskId))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            return "{\"error\":\"%s\"}".formatted(e.getMessage());
        }
    }
}
