package com.xuejiai.aaf.module.aigc.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.aigc.enums.MediaAssetType;
import com.xuejiai.aaf.module.aigc.service.MediaAssetService;
import com.xuejiai.aaf.module.aigc.vo.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 素材库管理接口。 */
@Tag(name = "AIGC 素材库")
@RestController
@RequestMapping("/api/aigc/assets")
@RequiredArgsConstructor
public class MediaAssetController {

    private final MediaAssetService mediaAssetService;

    @Operation(summary = "分页查询素材列表")
    @GetMapping
    public Result<Page<MediaAssetVO>> list(
            @RequestParam Long userId,
            @RequestParam(required = false) MediaAssetType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(mediaAssetService.list(userId, type, page, size));
    }

    @Operation(summary = "按标签筛选素材")
    @GetMapping("/by-tags")
    public Result<List<MediaAssetVO>> listByTags(@RequestParam List<Long> tagIds) {
        return Result.success(mediaAssetService.listByTags(tagIds));
    }

    @Operation(summary = "按分类筛选素材")
    @GetMapping("/by-category/{categoryId}")
    public Result<List<MediaAssetVO>> listByCategory(@PathVariable Long categoryId) {
        return Result.success(mediaAssetService.listByCategory(categoryId));
    }

    @Operation(summary = "获取素材详情")
    @GetMapping("/{id}")
    public Result<MediaAssetVO> getById(@PathVariable Long id) {
        return Result.success(mediaAssetService.getById(id));
    }

    @Operation(summary = "创建素材")
    @PostMapping
    public Result<MediaAssetVO> create(
            @RequestParam Long userId, @Valid @RequestBody MediaAssetCreateDTO dto) {
        return Result.success(mediaAssetService.create(userId, dto));
    }

    @Operation(summary = "更新素材")
    @PutMapping("/{id}")
    public Result<MediaAssetVO> update(
            @PathVariable Long id, @Valid @RequestBody MediaAssetUpdateDTO dto) {
        return Result.success(mediaAssetService.update(id, dto));
    }

    @Operation(summary = "删除素材")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mediaAssetService.delete(id);
        return Result.success();
    }

    @Operation(summary = "从生成结果保存到素材库")
    @PostMapping("/save-from-generation")
    public Result<MediaAssetVO> saveFromGeneration(
            @RequestParam Long userId, @Valid @RequestBody SaveFromGenerationDTO dto) {
        return Result.success(mediaAssetService.saveFromGeneration(userId, dto));
    }

    @Operation(summary = "AI 自动打标")
    @PostMapping("/{id}/auto-tag")
    public Result<List<String>> autoTag(@PathVariable Long id) {
        return Result.success(mediaAssetService.autoTag(id));
    }

    @Operation(summary = "重新生成变体")
    @PostMapping("/regenerate")
    public Result<MediaAssetVO> regenerate(
            @RequestParam Long userId, @Valid @RequestBody RegenerateRequest request) {
        return Result.success(mediaAssetService.regenerate(userId, request));
    }

    @Operation(summary = "批量生成变体")
    @PostMapping("/{id}/batch-variants")
    public Result<List<MediaAssetVO>> batchVariants(
            @RequestParam Long userId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "4") int count) {
        return Result.success(mediaAssetService.batchVariants(userId, id, count));
    }

    @Operation(summary = "查询素材变体列表")
    @GetMapping("/{id}/variants")
    public Result<List<MediaAssetVO>> listVariants(@PathVariable Long id) {
        return Result.success(mediaAssetService.listVariants(id));
    }
}
