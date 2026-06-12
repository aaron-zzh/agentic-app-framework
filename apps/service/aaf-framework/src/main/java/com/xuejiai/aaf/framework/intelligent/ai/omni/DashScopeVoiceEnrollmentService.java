package com.xuejiai.aaf.framework.intelligent.ai.omni;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于阿里云 DashScope qwen-voice-enrollment 接口的声音复刻实现。
 *
 * <p>三个操作（create / list / delete）均通过同一 HTTP 端点，以 {@code action} 字段区分。 音频限制：WAV/MP3/M4A，10~60 秒，< 10
 * MB，采样率 ≥ 24 kHz，单声道。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeVoiceEnrollmentService implements VoiceEnrollmentService {

    private static final String API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization";
    private static final String ENROLLMENT_MODEL = "qwen-voice-enrollment";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DashScopeVoiceEnrollmentService(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String createVoice(CreateVoiceRequest request) {
        try {
            var input =
                    objectMapper
                            .createObjectNode()
                            .put("action", "create")
                            .put("target_model", request.targetModel())
                            .put("preferred_name", request.preferredName());
            input.putObject("audio").put("data", request.audioData());
            if (request.text() != null) {
                input.put("text", request.text());
            }
            if (request.language() != null) {
                input.put("language", request.language());
            }

            var body = objectMapper.createObjectNode().put("model", ENROLLMENT_MODEL);
            body.set("input", input);

            var root = post(body.toString());
            var voice = root.get("output").get("voice").asText();
            log.info(
                    "[VoiceEnrollment] 音色创建成功: voice={}, targetModel={}",
                    voice,
                    request.targetModel());
            return voice;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("声音复刻失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VoiceInfo> listVoices(int pageIndex, int pageSize) {
        try {
            var input =
                    objectMapper
                            .createObjectNode()
                            .put("action", "list")
                            .put("page_index", pageIndex)
                            .put("page_size", pageSize);
            var body = objectMapper.createObjectNode().put("model", ENROLLMENT_MODEL);
            body.set("input", input);

            var root = post(body.toString());
            var voiceList = root.get("output").get("voice_list");
            List<VoiceInfo> result = new ArrayList<>();
            if (voiceList != null) {
                voiceList.forEach(
                        item ->
                                result.add(
                                        new VoiceInfo(
                                                item.get("voice").asText(),
                                                item.get("gmt_create").asText(),
                                                item.get("target_model").asText())));
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("查询音色列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteVoice(String voice) {
        try {
            var input = objectMapper.createObjectNode().put("action", "delete").put("voice", voice);
            var body = objectMapper.createObjectNode().put("model", ENROLLMENT_MODEL);
            body.set("input", input);

            post(body.toString());
            log.info("[VoiceEnrollment] 音色已删除: voice={}", voice);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("删除音色失败: " + e.getMessage(), e);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode post(String json) throws Exception {
        var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        var root = objectMapper.readTree(response.body());

        if (response.statusCode() != 200) {
            var msg = root.has("message") ? root.get("message").asText() : response.body();
            throw new RuntimeException("DashScope API 错误 [" + response.statusCode() + "]: " + msg);
        }
        return root;
    }
}
