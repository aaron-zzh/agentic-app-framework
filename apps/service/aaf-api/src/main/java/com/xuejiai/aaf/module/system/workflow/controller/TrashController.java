package com.xuejiai.aaf.module.system.workflow.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.workflow.service.TrashService;
import com.xuejiai.aaf.module.system.workflow.vo.TrashItemVO;
import com.xuejiai.aaf.module.system.workflow.vo.TrashPageDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 回收站接口。 */
@Tag(name = "回收站")
@RestController
@RequestMapping("/api/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    @Operation(summary = "分页查询回收站")
    @GetMapping
    public Result<PageResult<TrashItemVO>> page(@Validated @ParameterObject TrashPageDTO request) {
        return Result.success(trashService.page(request));
    }

    @Operation(summary = "恢复已删除记录")
    @PostMapping("/restore")
    public Result<Void> restore(@RequestParam String entityType, @RequestBody List<Long> ids) {
        trashService.restore(entityType, ids);
        return Result.success();
    }

    @Operation(summary = "彻底删除记录")
    @DeleteMapping("/purge")
    public Result<Void> purge(@RequestParam String entityType, @RequestBody List<Long> ids) {
        trashService.purge(entityType, ids);
        return Result.success();
    }
}
