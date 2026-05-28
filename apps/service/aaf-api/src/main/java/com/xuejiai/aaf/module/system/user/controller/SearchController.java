package com.xuejiai.aaf.module.system.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.user.service.SearchService;
import com.xuejiai.aaf.module.system.user.vo.SearchResultVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 全局搜索接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "全局搜索")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "全局搜索", description = "跨实体搜索，返回分组结果")
    @GetMapping
    public Result<List<SearchResultVO>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "all") String entities,
            @RequestParam(defaultValue = "5") int limit) {
        return Result.success(searchService.search(q, entities, limit));
    }
}
