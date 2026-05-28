package com.xuejiai.aaf.module.system.workflow.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.workflow.service.DelegationService;
import com.xuejiai.aaf.module.system.workflow.vo.DelegationCreateDTO;
import com.xuejiai.aaf.module.system.workflow.vo.DelegationPageDTO;
import com.xuejiai.aaf.module.system.workflow.vo.DelegationVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 审批委托接口
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "审批委托")
@RestController
@RequestMapping("/api/delegations")
@RequiredArgsConstructor
public class DelegationController {

    private final DelegationService delegationService;
    private final ActorContext actorContext;

    @Operation(summary = "创建委托")
    @PostMapping
    public Result<DelegationVO> create(@Validated @RequestBody DelegationCreateDTO dto) {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(delegationService.create(userId, dto));
    }

    @Operation(summary = "分页查询委托")
    @GetMapping
    public Result<PageResult<DelegationVO>> page(
            @Validated @ParameterObject DelegationPageDTO request) {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(delegationService.page(userId, request));
    }

    @Operation(summary = "取消委托")
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        Long userId = actorContext.currentUserId().orElseThrow();
        delegationService.cancel(userId, id);
        return Result.success();
    }
}
