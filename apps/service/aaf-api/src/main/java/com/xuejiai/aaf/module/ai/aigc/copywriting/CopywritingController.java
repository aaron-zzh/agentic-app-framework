package com.xuejiai.aaf.module.ai.aigc.copywriting;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.copywriting.vo.CopywritingAssetPageVO;
import com.xuejiai.aaf.module.ai.assistant.vo.AssistantExecutionEventVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/** 文案生成接口（结构化 SSE 流式）。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "文案生成")
@RestController
@RequestMapping("/api/aigc/copywriting")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
public class CopywritingController {

    private final CopywritingService copywritingService;

    public record GenerateRequest(
            @NotBlank String prompt,
            String modelId,
            String type,
            String template,
            String length,
            String translateTo,
            List<String> referenceImageKeys) {}

    public record AnalysisGenerateRequest(
            @NotBlank String analysis, String modelId, String userNotes) {}

    public record RewriteRequest(@NotBlank String content, String modelId) {}

    public record AnalyzeRequest(@NotBlank String content, String modelId) {}

    @Operation(summary = "分页查询文案资产")
    @GetMapping("/assets")
    public Result<CopywritingAssetPageVO> assets(
            @RequestParam(defaultValue = "ALL") CopywritingLinkStatus linkStatus,
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return Result.success(
                copywritingService.assets(linkStatus, projectId, keyword, pageNo, pageSize));
    }

    @Operation(summary = "流式生成文案")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AssistantExecutionEventVO> generate(@Valid @RequestBody GenerateRequest request) {
        return copywritingService.generateEvents(
                request.modelId(),
                request.type(),
                request.prompt(),
                request.template(),
                request.length(),
                request.translateTo(),
                request.referenceImageKeys());
    }

    @Operation(summary = "流式生成文案（爆款复制场景：参考爆款结构分析创作）")
    @PostMapping(value = "/generate-from-analysis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AssistantExecutionEventVO> generateFromAnalysis(
            @Valid @RequestBody AnalysisGenerateRequest request) {
        return copywritingService.generateFromAnalysisEvents(
                request.modelId(), request.analysis(), request.userNotes());
    }

    @Operation(summary = "流式改写文案")
    @PostMapping(value = "/rewrite", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AssistantExecutionEventVO> rewrite(@Valid @RequestBody RewriteRequest request) {
        return copywritingService.rewriteEvents(request.modelId(), request.content());
    }

    @Operation(summary = "流式分析爆款结构（content-judge）")
    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AssistantExecutionEventVO> analyze(@Valid @RequestBody AnalyzeRequest request) {
        return copywritingService.analyzeEvents(request.modelId(), request.content());
    }
}
