package com.xuejiai.aaf.module.ai.aigc.history.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.ai.aigc.history.domain.GenerationHistory;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.history.repository.GenerationHistoryRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 生成历史接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "AIGC 生成历史")
@RestController
@RequestMapping("/api/aigc/history")
@RequiredArgsConstructor
public class GenerationHistoryController {

    private final GenerationHistoryRepository historyRepository;

    @Operation(summary = "查询生成历史")
    @GetMapping
    public Result<Page<GenerationHistory>> list(
            @RequestParam Long userId,
            @RequestParam(required = false) MediaAssetType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        if (type != null) {
            return Result.success(historyRepository.findByUserIdAndType(userId, type, pageable));
        }
        return Result.success(historyRepository.findByUserId(userId, pageable));
    }
}
