package com.xuejiai.aaf.module.brokerage.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.brokerage.service.BrokerageMeService;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteCodeMeVO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInviteRewardConfigVO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageInvitedUserVO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawVO;
import com.xuejiai.aaf.module.brokerage.vo.WithdrawApplyDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 当前用户的邀请奖励 + 提现接口（user-facing）。
 *
 * @author AaronZZH &amp; Kiro
 */
@Tag(name = "我的邀请奖励与提现")
@RestController
@RequestMapping("/api/brokerage/me")
@RequiredArgsConstructor
public class BrokerageMeController {

    private final BrokerageMeService brokerageMeService;
    private final OperatorContext operatorContext;

    // ==================== 邀请码 ====================

    @Operation(summary = "取/生成我的邀请码")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/invite-code")
    public Result<BrokerageInviteCodeMeVO> getMyInviteCode(
            @RequestParam(required = false) String channel) {
        return Result.success(brokerageMeService.getOrCreateMyInviteCode(currentUserId(), channel));
    }

    @Operation(summary = "我邀请的好友列表（分页）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/invite-history")
    public Result<PageResult<BrokerageInvitedUserVO>> getInviteHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(brokerageMeService.listMyInvitedUsers(currentUserId(), page, size));
    }

    @Operation(summary = "邀请奖励配置（前端展示）")
    @GetMapping("/invite-rewards")
    public Result<BrokerageInviteRewardConfigVO> getInviteRewards() {
        return Result.success(brokerageMeService.getRewardConfig());
    }

    // ==================== 提现 ====================

    @Operation(summary = "我的可用佣金余额（分）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/balance")
    public Result<Long> getBalance() {
        return Result.success(brokerageMeService.getMyBalance(currentUserId()));
    }

    @Operation(summary = "申请提现（需已绑手机号）")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/withdraw")
    public Result<BrokerageWithdrawVO> applyWithdraw(@Validated @RequestBody WithdrawApplyDTO dto) {
        return Result.success(brokerageMeService.applyWithdraw(currentUserId(), dto));
    }

    @Operation(summary = "我的提现历史（分页）")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/withdraws")
    public Result<PageResult<BrokerageWithdrawVO>> listWithdraws(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(brokerageMeService.listMyWithdraws(currentUserId(), page, size));
    }

    // ==================== 私有 ====================

    private Long currentUserId() {
        return operatorContext
                .currentUserId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
    }
}
