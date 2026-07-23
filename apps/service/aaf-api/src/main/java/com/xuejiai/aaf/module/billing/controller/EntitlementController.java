package com.xuejiai.aaf.module.billing.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.domain.EntitlementQuota;
import com.xuejiai.aaf.module.billing.service.EntitlementQuotaCrudService;
import com.xuejiai.aaf.module.billing.service.EntitlementService;
import com.xuejiai.aaf.module.billing.vo.EntitlementQuotaPageParam;
import com.xuejiai.aaf.module.billing.vo.EntitlementQuotaVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "权益额度管理")
@RestController
@RequestMapping("/api/billing/entitlement-quotas")
@RequiredArgsConstructor
public class EntitlementController
        extends BaseCrudController<
                EntitlementQuota, EntitlementQuotaVO, Void, Void, EntitlementQuotaPageParam> {

    private final EntitlementQuotaCrudService quotaCrudService;
    private final EntitlementService entitlementService;
    private final OperatorContext operatorContext;

    @Override
    protected BaseCrudService<
                    EntitlementQuota, EntitlementQuotaVO, Void, Void, EntitlementQuotaPageParam>
            getService() {
        return quotaCrudService;
    }

    @Operation(summary = "当前用户权益额度")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<List<EntitlementQuotaVO>> me() {
        return Result.success(quotaCrudService.listForUser(currentUserId()));
    }

    @Operation(summary = "重置已到期权益额度")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/reset")
    public Result<Integer> reset() {
        return Result.success(entitlementService.resetExpiredQuotas());
    }

    private Long currentUserId() {
        return operatorContext
                .currentUserId()
                .orElseThrow(
                        () ->
                                new com.xuejiai.aaf.common.exception.BusinessException(
                                        com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                .UNAUTHORIZED,
                                        "请先登录"));
    }
}
