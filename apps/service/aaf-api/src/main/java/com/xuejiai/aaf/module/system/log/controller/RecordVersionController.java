package com.xuejiai.aaf.module.system.log.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.log.domain.RecordVersion;
import com.xuejiai.aaf.module.system.log.service.RecordVersionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 版本快照接口。 */
@Tag(name = "版本快照")
@RestController
@RequestMapping("/api/{entity}/{id}/versions")
@RequiredArgsConstructor
public class RecordVersionController {

    private final RecordVersionService recordVersionService;

    @Operation(summary = "获取版本列表")
    @GetMapping
    public Result<List<RecordVersion>> listVersions(
            @PathVariable("entity") String entity, @PathVariable("id") Long id) {
        return Result.success(recordVersionService.listVersions(entity, id));
    }

    @Operation(summary = "恢复到指定版本")
    @PostMapping("/{v}/restore")
    public Result<String> restore(
            @PathVariable("entity") String entity,
            @PathVariable("id") Long id,
            @PathVariable("v") Integer v) {
        String data = recordVersionService.getVersionData(entity, id, v);
        // TODO: 根据 entity 类型反序列化并写回对应表，当前仅返回快照数据
        return Result.success(data);
    }
}
