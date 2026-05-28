/**
 * 记忆管理接口。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.intelligent.memory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "记忆管理")
@RestController
@RequestMapping("/api/ai/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryManagementService memoryService;

    @Operation(summary = "记忆列表（分页）")
    @GetMapping
    public Result<PageResult<MemoryAtomVO>> list(
            @RequestParam(required = false) String scope, Pageable pageable) {
        return Result.success(memoryService.list(scope, pageable));
    }

    @Operation(summary = "搜索记忆")
    @GetMapping("/search")
    public Result<List<MemoryAtomVO>> search(
            @RequestParam String keyword, @RequestParam(required = false) String scope) {
        return Result.success(memoryService.search(keyword, scope));
    }

    @Operation(summary = "记忆统计")
    @GetMapping("/stats")
    public Result<MemoryStatsVO> stats() {
        return Result.success(memoryService.getStats());
    }

    @Operation(summary = "删除记忆")
    @DeleteMapping
    public Result<Void> delete(@RequestBody List<UUID> ids) {
        memoryService.delete(ids);
        return Result.success();
    }

    @Operation(summary = "清空指定范围的记忆")
    @DeleteMapping("/scope/{scope}")
    public Result<Void> clearByScope(@PathVariable String scope) {
        memoryService.clearByScope(scope);
        return Result.success();
    }
}
