package com.xuejiai.aaf.module.billing.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.repository.EntitlementDefRepository;
import com.xuejiai.aaf.module.billing.service.EntitlementService;
import com.xuejiai.aaf.module.billing.vo.EntitlementQuotaVO;

import lombok.RequiredArgsConstructor;

/** 权益额度接口 */
@RestController
@RequestMapping("/api/billing/entitlement")
@RequiredArgsConstructor
public class EntitlementController {

    private final EntitlementService entitlementService;
    private final EntitlementDefRepository defRepository;
    private final OperatorContext operatorContext;

    /** 查询用户所有权益额度 */
    @GetMapping("/quotas")
    public Result<List<EntitlementQuotaVO>> listQuotas(
            @RequestParam(required = false) Long userId) {
        var quotas = entitlementService.listUserQuotas(ownerId(userId));
        var vos =
                quotas.stream()
                        .map(
                                q -> {
                                    var def = defRepository.findById(q.getEntId()).orElse(null);
                                    return new EntitlementQuotaVO(
                                            q.getId(),
                                            def != null ? def.getCode() : null,
                                            def != null ? def.getName() : null,
                                            def != null ? def.getType() : null,
                                            def != null ? def.getUnit() : null,
                                            q.getTotal(),
                                            q.getUsed(),
                                            q.getRemain(),
                                            q.getNextResetAt());
                                })
                        .toList();
        return Result.success(vos);
    }

    /** 手动触发周期重置（供定时任务或管理后台调用） */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reset-expired")
    public Result<Integer> resetExpired() {
        return Result.success(entitlementService.resetExpiredQuotas());
    }

    private Long ownerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
