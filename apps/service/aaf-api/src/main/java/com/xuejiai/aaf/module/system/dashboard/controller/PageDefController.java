package com.xuejiai.aaf.module.system.dashboard.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.dashboard.service.PageDefService;
import com.xuejiai.aaf.module.system.dashboard.vo.PageDefCreateDTO;
import com.xuejiai.aaf.module.system.dashboard.vo.PageDefVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 页面定义接口（PageEngine）。 */
@Tag(name = "页面定义")
@RestController
@RequestMapping("/api/system/page-defs")
@RequiredArgsConstructor
public class PageDefController {

    private final PageDefService pageDefService;

    @Operation(summary = "查询全量页面定义")
    @GetMapping
    public Result<List<PageDefVO>> list() {
        return Result.success(pageDefService.listAll());
    }

    @Operation(summary = "查询单个页面定义")
    @GetMapping("/{id}")
    public Result<PageDefVO> get(@PathVariable Long id) {
        return Result.success(pageDefService.getById(id));
    }

    @Operation(summary = "根据 slug 获取已发布的页面定义")
    @GetMapping("/slug/**")
    public Result<PageDefVO> getBySlug(jakarta.servlet.http.HttpServletRequest request) {
        String slug = request.getRequestURI().replaceFirst(".*/slug/", "");
        return Result.success(pageDefService.getPublishedBySlug(slug));
    }

    @Operation(summary = "创建页面定义")
    @PostMapping
    public Result<PageDefVO> create(@Validated @RequestBody PageDefCreateDTO dto) {
        return Result.success(pageDefService.create(dto));
    }

    @Operation(summary = "更新页面定义")
    @PutMapping("/{id}")
    public Result<PageDefVO> update(
            @PathVariable Long id, @Validated @RequestBody PageDefCreateDTO dto) {
        return Result.success(pageDefService.update(id, dto));
    }

    @Operation(summary = "发布页面定义")
    @PostMapping("/{id}/publish")
    public Result<PageDefVO> publish(@PathVariable Long id) {
        return Result.success(pageDefService.publish(id));
    }

    @Operation(summary = "回滚页面定义（取消发布）")
    @PostMapping("/{id}/rollback")
    public Result<PageDefVO> rollback(@PathVariable Long id) {
        return Result.success(pageDefService.rollback(id));
    }

    @Operation(summary = "删除页面定义")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        pageDefService.delete(id);
        return Result.success();
    }
}
