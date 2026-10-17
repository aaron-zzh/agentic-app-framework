package com.xuejiai.aaf.module.device.controller;

import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_OWNER_CREDITS_INSUFFICIENT;
import static com.xuejiai.aaf.module.device.ErrorCodeConstants.DEVICE_VOICE_GATEWAY_FAILED;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.module.device.security.DevicePrincipal;
import com.xuejiai.aaf.module.device.service.DeviceVoiceGatewayService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;

/** 设备凭据专用的助理对话与语音合成出口。 */
@Slf4j
@Tag(name = "设备语音网关")
@RestController
@RequestMapping("/api/device")
@PreAuthorize("hasRole('DEVICE')")
public class DeviceVoiceGatewayController {

    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final DeviceVoiceGatewayService gatewayService;

    public DeviceVoiceGatewayController(DeviceVoiceGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Operation(summary = "通过设备与助理对话")
    @PostMapping(
            value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @AuthenticationPrincipal DevicePrincipal principal,
            @Valid @RequestBody ChatRequest request) {
        var stream = gatewayService.chat(principal, request.text(), request.conversationKey());
        var emitter = new SseEmitter(600_000L);
        stream.text()
                .subscribe(
                        text -> send(emitter, "message", Map.of("text", text)),
                        failure -> {
                            var error = error(failure);
                            send(
                                    emitter,
                                    "error",
                                    Map.of("code", error.getCode(), "message", error.getMessage()));
                            emitter.complete();
                        },
                        () -> {
                            send(
                                    emitter,
                                    "done",
                                    Map.of("threadId", stream.threadId(), "runId", stream.runId()));
                            emitter.complete();
                        });
        return emitter;
    }

    @Operation(summary = "流式合成设备语音")
    @PostMapping(
            value = "/voice/tts",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "audio/mpeg")
    public ResponseEntity<StreamingResponseBody> tts(
            @AuthenticationPrincipal DevicePrincipal principal,
            @Valid @RequestBody TtsRequest request) {
        var audio = gatewayService.synthesize(principal, request.text(), request.voice());
        StreamingResponseBody body =
                output -> {
                    for (var chunk : audio.toIterable()) {
                        output.write(chunk);
                        output.flush();
                    }
                };
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private static BusinessException error(Throwable failure) {
        var current = failure;
        while (current != null) {
            if (current instanceof BusinessException businessException) {
                return businessException;
            }
            if (current instanceof InsufficientCreditsException) {
                return new BusinessException(DEVICE_OWNER_CREDITS_INSUFFICIENT);
            }
            current = current.getCause();
        }
        log.error("设备语音对话流异常", failure);
        return new BusinessException(DEVICE_VOICE_GATEWAY_FAILED);
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    public record ChatRequest(
            @NotBlank @Size(max = 4000) String text, @Size(max = 64) String conversationKey) {}

    public record TtsRequest(
            @NotBlank @Size(max = 4000) String text, @Size(max = 128) String voice) {}
}
