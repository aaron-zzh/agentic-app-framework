package com.xuejiai.aaf.module.pay.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.vo.BizOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.BizOrderVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 业务订单接口 */
@Tag(name = "业务订单")
@RestController
@RequestMapping("/api/biz/orders")
@RequiredArgsConstructor
public class BizOrderController {

    private final BizOrderService bizOrderService;

    @Operation(summary = "创建业务订单")
    @PostMapping
    public Result<BizOrderVO> create(
            @RequestParam Long userId, @Valid @RequestBody BizOrderCreateDTO dto) {
        return Result.success(bizOrderService.create(userId, dto));
    }

    @Operation(summary = "查询用户订单列表")
    @GetMapping
    public Result<PageResult<BizOrderVO>> list(
            @RequestParam Long userId, @PageableDefault Pageable pageable) {
        var page = bizOrderService.listByUser(userId, pageable);
        return Result.success(new PageResult<>(page.getContent(), page.getTotalElements()));
    }

    @Operation(summary = "查询订单详情")
    @GetMapping("/{id}")
    public Result<BizOrderVO> getById(@PathVariable Long id) {
        return Result.success(bizOrderService.getById(id));
    }
}
