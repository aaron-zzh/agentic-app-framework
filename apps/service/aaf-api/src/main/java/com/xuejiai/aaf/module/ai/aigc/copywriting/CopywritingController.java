package com.xuejiai.aaf.module.ai.aigc.copywriting;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.util.JsonUtils;

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
            @NotBlank String topic,
            String modelId,
            String type,
            String template,
            String length,
            String translateTo,
            String referenceAnalysis,
            String userNotes) {}

    public record RewriteRequest(@NotBlank String content, String modelId) {}

    public record AnalyzeRequest(@NotBlank String content, String modelId) {}

    @Operation(summary = "流式生成文案")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generate(@Valid @RequestBody GenerateRequest req) {
        return encodeStream(
                copywritingService.generate(
                        req.modelId(),
                        req.type(),
                        req.topic(),
                        req.template(),
                        req.length(),
                        req.translateTo(),
                        req.referenceAnalysis(),
                        req.userNotes()));
    }

    @Operation(summary = "流式改写文案")
    @PostMapping(value = "/rewrite", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> rewrite(@Valid @RequestBody RewriteRequest req) {
        return encodeStream(copywritingService.rewrite(req.modelId(), req.content()));
    }

    @Operation(summary = "流式分析爆款结构（content-judge）")
    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyze(@Valid @RequestBody AnalyzeRequest req) {
        return encodeStream(copywritingService.analyze(req.modelId(), req.content()));
    }

    /**
     * 将原始文本 token 流编码为 JSON 字符串后经 SSE 下发。
     *
     * <p>Spring 的 {@code Flux<String>} SSE 编码会写成 {@code data:<原始文本>}，缺少 SSE 规范约定的「分隔空格」。 当 token
     * 以空格开头时，消费端按规范删除 {@code data:} 后的首个空格，导致前导空格丢失（如 markdown 的 {@code "## 标题"} 变成 {@code
     * "##标题"}）。改为 JSON 编码（{@code data:" token"}）可无损保留空格、换行等字符；前端 {@code ai-stream.ts} 解析出字符串后直接作为
     * token 输出。
     *
     * <p>错误以 {@code [ERROR]} 前缀的字符串下发（同样经 JSON 编码），前端解析后识别该前缀转为错误回调。
     */
    private Flux<String> encodeStream(Flux<String> tokens) {
        return tokens.onErrorResume(e -> Flux.just("[ERROR]" + e.getMessage())).map(this::toJson);
    }

    private String toJson(String token) {
        try {
            return JsonUtils.toJsonString(token);
        } catch (Exception e) {
            return "\"\"";
        }
    }
}
