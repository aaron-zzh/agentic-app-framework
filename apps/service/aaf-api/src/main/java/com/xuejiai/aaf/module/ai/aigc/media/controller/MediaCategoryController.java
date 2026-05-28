package com.xuejiai.aaf.module.ai.aigc.media.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaCategoryService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaCategoryCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.media.vo.MediaCategoryVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 素材分类管理接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AIGC 素材分类")
@RestController
@RequestMapping("/api/aigc/categories")
@RequiredArgsConstructor
public class MediaCategoryController {

    private final MediaCategoryService categoryService;

    @Operation(summary = "获取分类树")
    @GetMapping
    public Result<List<MediaCategoryVO>> tree() {
        return Result.success(categoryService.tree());
    }

    @Operation(summary = "创建分类")
    @PostMapping
    public Result<MediaCategoryVO> create(@Valid @RequestBody MediaCategoryCreateDTO dto) {
        return Result.success(categoryService.create(dto));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/{id}")
    public Result<MediaCategoryVO> update(
            @PathVariable Long id, @Valid @RequestBody MediaCategoryCreateDTO dto) {
        return Result.success(categoryService.update(id, dto));
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
