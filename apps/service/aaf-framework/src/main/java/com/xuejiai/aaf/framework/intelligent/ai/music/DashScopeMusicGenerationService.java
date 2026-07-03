package com.xuejiai.aaf.framework.intelligent.ai.music;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 基于阿里云百炼 fun-music-v1 HTTP API 的音乐生成实现。
 *
 * <p>非流式模式：同步阻塞返回完整音频 URL。
 */
@Slf4j
@Service("dashScopeMusicGenerationService")
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeMusicGenerationService implements MusicGenerationService {

    private static final String API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/audio/music/generation";
    private static final String DEFAULT_MODEL = "fun-music-v1";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DashScopeMusicGenerationService(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public MusicResult generate(AiModel model, MusicRequest request) {
        try {
            var body = buildRequestBody(model, request);
            var json = JsonUtils.toJsonString(body);

            var httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(API_URL))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            var root = JsonUtils.readTree(response.body());

            if (root.has("code")) {
                var errMsg = root.has("message") ? root.get("message").asString() : "未知错误";
                throw new RuntimeException("音乐生成失败: " + errMsg);
            }

            return parseResult(root);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("音乐生成失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildRequestBody(AiModel model, MusicRequest request) {
        var body = new HashMap<String, Object>();
        String modelName =
                (model != null
                                && org.springframework.util.StringUtils.hasText(
                                        model.getModelName()))
                        ? model.getModelName()
                        : DEFAULT_MODEL;
        body.put("model", modelName);

        var input = new HashMap<String, Object>();
        if (request.lyrics() != null) {
            input.put("lyrics", request.lyrics());
        } else if (request.prompt() != null) {
            input.put("prompt", request.prompt());
        }
        if (request.gender() != null) {
            input.put("gender", request.gender());
        }
        if (request.format() != null) {
            input.put("format", request.format());
        }
        body.put("input", input);
        return body;
    }

    private MusicResult parseResult(JsonNode root) {
        var requestId = root.has("request_id") ? root.get("request_id").asString() : null;
        var output = root.get("output");
        var audio = output.get("audio");

        var audioUrl = audio.has("url") ? audio.get("url").asString() : null;

        String lyrics = null;
        Integer sampleRate = null;
        Integer channels = null;
        if (output.has("extra_info")) {
            var extraInfo = output.get("extra_info");
            lyrics = extraInfo.has("lyrics") ? extraInfo.get("lyrics").asString() : null;
            sampleRate = extraInfo.has("sample_rate") ? extraInfo.get("sample_rate").asInt() : null;
            channels = extraInfo.has("channels") ? extraInfo.get("channels").asInt() : null;
        }

        Integer duration = null;
        if (root.has("usage") && root.get("usage").has("duration")) {
            duration = root.get("usage").get("duration").asInt();
        }

        log.info("[FunMusic] 音乐生成完成: requestId={}, duration={}s", requestId, duration);
        return new MusicResult(requestId, audioUrl, lyrics, duration, sampleRate, channels);
    }
}
