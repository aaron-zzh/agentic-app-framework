package com.xuejiai.aaf.module.ui.aiui;

import java.util.List;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * AI UI 接口：组件生成、布局优化、组件推荐。
 */
@Tag(name = "AI UI")
@RestController
@RequestMapping("/api/ui/aiui")
@RequiredArgsConstructor
public class AiuiController {

    private final AiuiService aiuiService;

    @Operation(summary = "AI 生成 EntityDef")
    @PostMapping("/generate")
    public Result<AiuiGenerateVO> generate(@Validated @RequestBody AiuiGenerateDTO dto) {
        return Result.success(aiuiService.generate(dto));
    }

    @Operation(summary = "AI 优化布局")
    @PostMapping("/optimize-layout")
    public Result<String> optimizeLayout(@RequestBody List<String> fields) {
        return Result.success(aiuiService.optimizeLayout(fields));
    }

    @Operation(summary = "AI 推荐组件")
    @PostMapping("/recommend")
    public Result<String> recommend(@RequestBody Map<String, Object> context) {
        return Result.success(aiuiService.recommend(context));
    }
}
