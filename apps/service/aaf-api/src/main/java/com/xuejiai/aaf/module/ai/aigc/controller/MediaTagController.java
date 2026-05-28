package com.xuejiai.aaf.module.ai.aigc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.aigc.service.MediaTagService;
import com.xuejiai.aaf.module.ai.aigc.vo.MediaTagCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.vo.MediaTagVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 素材标签管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AIGC 素材标签")
@RestController
@RequestMapping("/api/aigc/tags")
@RequiredArgsConstructor
public class MediaTagController {

    private final MediaTagService tagService;

    @Operation(summary = "查询所有标签")
    @GetMapping
    public Result<List<MediaTagVO>> list() {
        return Result.success(tagService.list());
    }

    @Operation(summary = "创建标签")
    @PostMapping
    public Result<MediaTagVO> create(@Valid @RequestBody MediaTagCreateDTO dto) {
        return Result.success(tagService.create(dto));
    }

    @Operation(summary = "更新标签")
    @PutMapping("/{id}")
    public Result<MediaTagVO> update(
            @PathVariable Long id, @Valid @RequestBody MediaTagCreateDTO dto) {
        return Result.success(tagService.update(id, dto));
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.success();
    }

    @Operation(summary = "为素材绑定标签")
    @PostMapping("/bind/{assetId}")
    public Result<Void> bindTags(@PathVariable Long assetId, @RequestBody List<Long> tagIds) {
        tagService.bindTags(assetId, tagIds);
        return Result.success();
    }

    @Operation(summary = "获取素材的标签")
    @GetMapping("/asset/{assetId}")
    public Result<List<MediaTagVO>> getByAsset(@PathVariable Long assetId) {
        return Result.success(tagService.getTagsByAssetId(assetId));
    }
}
