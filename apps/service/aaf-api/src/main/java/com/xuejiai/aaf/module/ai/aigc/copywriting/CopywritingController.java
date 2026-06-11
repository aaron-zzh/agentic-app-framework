package com.xuejiai.aaf.module.ai.aigc.copywriting;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/** 文案生成接口（SSE 流式）。 */
@Tag(name = "文案生成")
@RestController
@RequestMapping("/api/aigc/copywriting")
@RequiredArgsConstructor
public class CopywritingController {

    private final CopywritingService copywritingService;

    public record GenerateRequest(
            @NotBlank String topic, String modelId, String type, String template, String length) {}

    public record RewriteRequest(@NotBlank String content, String modelId) {}

    @Operation(summary = "流式生成文案")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generate(@Valid @RequestBody GenerateRequest req) {
        return copywritingService.generate(
                req.modelId(), req.type(), req.topic(), req.template(), req.length());
    }

    @Operation(summary = "流式改写文案")
    @PostMapping(value = "/rewrite", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> rewrite(@Valid @RequestBody RewriteRequest req) {
        return copywritingService.rewrite(req.modelId(), req.content());
    }
}
