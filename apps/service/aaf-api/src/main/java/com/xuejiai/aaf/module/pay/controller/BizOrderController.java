package com.xuejiai.aaf.module.pay.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.vo.BizOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.BizOrderItemVO;
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
    private final OperatorContext operatorContext;

    @Operation(summary = "创建业务订单")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Result<BizOrderVO> create(
            @RequestParam(required = false) Long userId, @Valid @RequestBody BizOrderCreateDTO dto) {
        return Result.success(bizOrderService.create(ownerId(userId), dto));
    }

    @Operation(summary = "查询用户订单列表")
    @GetMapping
    public Result<PageResult<BizOrderVO>> list(
            @RequestParam(required = false) Long userId, @PageableDefault Pageable pageable) {
        var page = bizOrderService.listByUser(ownerId(userId), pageable);
        return Result.success(new PageResult<>(page.getContent(), page.getTotalElements()));
    }

    @Operation(summary = "查询订单详情")
    @GetMapping("/{id}")
    public Result<BizOrderVO> getById(@PathVariable Long id) {
        return Result.success(bizOrderService.getById(id));
    }

    @Operation(summary = "查询订单明细行")
    @GetMapping("/{id}/items")
    public Result<List<BizOrderItemVO>> getItems(@PathVariable Long id) {
        return Result.success(bizOrderService.getItems(id));
    }

    private Long ownerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
