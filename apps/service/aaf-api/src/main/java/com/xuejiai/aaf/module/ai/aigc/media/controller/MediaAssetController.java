package com.xuejiai.aaf.module.ai.aigc.media.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaAssetCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaAssetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaAssetVO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.RegenerateRequest;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 素材库管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AIGC 素材库")
@RestController
@RequestMapping("/api/aigc/assets")
@RequiredArgsConstructor
public class MediaAssetController {

    private final MediaAssetService assetService;
    private final OperatorContext operatorContext;

    @Operation(summary = "分页查询素材")
    @GetMapping
    public Result<Page<MediaAssetVO>> page(
            @RequestParam(required = false) MediaAssetType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(assetService.page(userId, type, categoryId, PageRequest.of(page, size)));
    }

    @Operation(summary = "搜索素材")
    @GetMapping("/search")
    public Result<List<MediaAssetVO>> search(@RequestParam String keyword) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(assetService.search(userId, keyword));
    }

    @Operation(summary = "获取素材详情")
    @GetMapping("/{id}")
    public Result<MediaAssetVO> getById(@PathVariable Long id) {
        return Result.success(assetService.getById(id));
    }

    @Operation(summary = "创建素材")
    @PostMapping
    public Result<MediaAssetVO> create(@Valid @RequestBody MediaAssetCreateDTO dto) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(assetService.create(userId, dto));
    }

    @Operation(summary = "更新素材")
    @PutMapping("/{id}")
    public Result<MediaAssetVO> update(
            @PathVariable Long id, @Valid @RequestBody MediaAssetUpdateDTO dto) {
        return Result.success(assetService.update(id, dto));
    }

    @Operation(summary = "删除素材")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return Result.success();
    }

    @Operation(summary = "从生成结果一键保存到素材库")
    @PostMapping("/save-from-generation")
    public Result<MediaAssetVO> saveFromGeneration(@Valid @RequestBody SaveFromGenerationDTO dto) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(assetService.saveFromGeneration(userId, dto));
    }

    @Operation(summary = "素材重新生成")
    @PostMapping("/regenerate")
    public Result<MediaAssetVO> regenerate(@Valid @RequestBody RegenerateRequest request) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(assetService.regenerate(userId, request));
    }

    @Operation(summary = "查询素材的所有变体")
    @GetMapping("/{id}/variants")
    public Result<List<MediaAssetVO>> getVariants(@PathVariable Long id) {
        return Result.success(assetService.getVariants(id));
    }

    @Operation(summary = "变体参数对比")
    @GetMapping("/{id}/variants/{variantId}/diff")
    public Result<String> getVariantDiff(@PathVariable Long id, @PathVariable Long variantId) {
        return Result.success(assetService.getVariantDiff(id, variantId));
    }
}
