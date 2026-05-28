package com.xuejiai.aaf.module.ai.voice.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 语音接口（STT + TTS）。
 *
 * <p>依赖 {@link SpeechService}，当前实现为 DashScopeSpeechService（需配置 DASHSCOPE_API_KEY）。 未配置时 Bean
 * 不存在，接口返回 503。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Tag(name = "语音服务")
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final SpeechService speechService;

    // ========== STT ==========

    @Operation(summary = "语音转文字（ASR）")
    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> stt(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "lang", defaultValue = "zh-CN") String lang) {
        try {
            String text = speechService.transcribe(audio.getBytes(), lang);
            return Result.success(text);
        } catch (Exception e) {
            log.error("STT 失败", e);
            return Result.error(500, "语音识别失败: " + e.getMessage());
        }
    }

    // ========== TTS ==========

    /** TTS 请求体 */
    public record TtsRequest(@NotBlank String text, String voice) {}

    @Operation(summary = "文字转语音（TTS，返回完整 MP3）")
    @PostMapping(value = "/tts", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> tts(@RequestBody TtsRequest request) {
        try {
            byte[] audio = speechService.synthesize(request.text(), request.voice());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(audio);
        } catch (Exception e) {
            log.error("TTS 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(summary = "文字转语音流式（TTS Stream，chunked audio/mpeg）")
    @PostMapping(value = "/tts/stream", produces = "audio/mpeg")
    public Flux<byte[]> ttsStream(@RequestBody TtsRequest request) {
        return speechService
                .synthesizeStream(request.text(), request.voice())
                .doOnError(e -> log.error("TTS 流式失败", e));
    }
}
