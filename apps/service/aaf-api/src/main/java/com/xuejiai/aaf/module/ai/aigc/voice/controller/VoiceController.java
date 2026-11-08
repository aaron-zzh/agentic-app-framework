package com.xuejiai.aaf.module.ai.aigc.voice.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.voice.service.VoiceTtsStreamService;
import com.xuejiai.aaf.module.ai.aigc.voice.vo.VoiceTtsStreamDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 语音接口（STT + 对话消息流式 TTS）。
 *
 * <p>配音作品走任务型接口 {@code /aigc/tasks/submit}（type=VOICE）；本处的 {@code /tts/stream} 用于对话消息即时播放。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "语音服务")
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class VoiceController {

    private final SpeechService speechService;
    private final VoiceTtsStreamService ttsStreamService;

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

    /** 流式语音合成：分块返回 PCM16 16kHz 单声道小端裸流。逐块写出并 flush，不整体缓冲。 */
    @Operation(summary = "流式语音合成（PCM16 16kHz 单声道）")
    @PostMapping(value = "/tts/stream", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> ttsStream(
            @Valid @RequestBody VoiceTtsStreamDTO request) {
        var audio = ttsStreamService.synthesizeStream(request.text(), request.voice());
        StreamingResponseBody body =
                output -> {
                    // 关闭 Stream 会取消上游订阅，客户端断开时随之取消合成任务
                    try (var chunks = audio.toStream()) {
                        var iterator = chunks.iterator();
                        while (iterator.hasNext()) {
                            output.write(iterator.next());
                            output.flush();
                        }
                    }
                };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
