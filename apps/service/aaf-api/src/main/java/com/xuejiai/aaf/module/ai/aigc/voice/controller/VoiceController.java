package com.xuejiai.aaf.module.ai.aigc.voice.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;

/**
 * 语音接口（STT）。
 *
 * <p>TTS 配音通过任务型接口 {@code /aigc/tasks/submit}（type=VOICE）提交，不在此处暴露直连接口。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "语音服务")
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final SpeechService speechService;

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
}
