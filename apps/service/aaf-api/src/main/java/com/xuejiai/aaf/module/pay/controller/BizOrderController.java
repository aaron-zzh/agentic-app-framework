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
            @Valid @RequestBody BizOrderCreateDTO dto) {
        return Result.success(bizOrderService.create(currentOwnerId(), dto));
    }

    @Operation(summary = "查询用户订单列表")
    @GetMapping
    public Result<PageResult<BizOrderVO>> list(
            @PageableDefault Pageable pageable) {
        var page = bizOrderService.listByUser(currentOwnerId(), pageable);
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

    /**
     * M1：当前身份来源唯一——只从 OperatorContext 取，取不到即 401。
     *
     * <p>原实现是 currentOwnerId().orElse(客户端传入的 userId)：一旦认证上下文解析不出归属者
     * （如 API Key 认证未绑定用户），就会采信请求参数里的 userId，形成任意用户数据读取。
     * 管理员代查须走带显式鉴权的管理端接口，不复用本接口。
     */
    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(
                        () ->
                                new com.xuejiai.aaf.common.exception.BusinessException(
                                        com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                .UNAUTHORIZED));
    }
}
