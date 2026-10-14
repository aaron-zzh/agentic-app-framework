package com.xuejiai.aaf.module.tool.websearch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;

import tools.jackson.databind.JsonNode;

/**
 * 阿里云信息查询服务（IQS）HTTP 公共客户端：统一认证、超时与错误解析。
 *
 * <p>认证 {@code Authorization: Bearer <API-KEY>}；联网搜索、健康图谱等 IQS 接口共用同一 Key 与域名。 配置见 {@code
 * aaf.tool.web-search.*}。工具始终注册（目录登记的工具必须有执行器，否则解析 Agent 工具时整体失败）， 未配置 api-key 时调用才报错。
 */
@Component
public class IqsHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 错误响应体写入异常信息时的最大长度。 */
    private static final int MAX_ERROR_BODY = 300;

    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    @Value("${aaf.tool.web-search.api-key:}")
    private String apiKey;

    @Value("${aaf.tool.web-search.base-url:https://cloud-iqs.aliyuncs.com}")
    private String baseUrl;

    /**
     * POST JSON 并返回解析后的响应体。
     *
     * @param path 接口路径，如 {@code /search/unified}
     * @param body 请求体对象，序列化为 JSON
     * @param timeout 单次请求超时
     * @throws IllegalStateException 未配置 API Key、请求失败、非 200 响应或响应无法解析；异常信息不含 API Key
     */
    public JsonNode post(String path, Object body, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("联网搜索服务未配置 IQS_API_KEY");
        }
        var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(trimTrailingSlash(baseUrl) + path))
                        .timeout(timeout)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJsonString(body)))
                        .build();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("IQS 请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("IQS 请求被中断", e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "IQS 返回 HTTP " + response.statusCode() + ": " + errorDetail(response.body()));
        }
        try {
            return JsonUtils.readTree(response.body());
        } catch (RuntimeException e) {
            throw new IllegalStateException("IQS 响应无法解析: " + e.getMessage(), e);
        }
    }

    /** 优先取扁平错误体的 errorCode/errorMessage（以 errorCode 为准），取不到则截断原文。 */
    private static String errorDetail(String responseBody) {
        try {
            var error = JsonUtils.readTree(responseBody);
            var code = error.path("errorCode").asString(error.path("code").asString(""));
            var message = error.path("errorMessage").asString(error.path("message").asString(""));
            if (!code.isEmpty() || !message.isEmpty()) {
                return code + ": " + message;
            }
        } catch (RuntimeException ignored) {
            // 非 JSON 错误体，退回截断原文
        }
        return abbreviate(responseBody, MAX_ERROR_BODY);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
