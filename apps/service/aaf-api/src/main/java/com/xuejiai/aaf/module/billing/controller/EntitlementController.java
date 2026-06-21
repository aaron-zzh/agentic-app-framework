package com.xuejiai.aaf.module.billing.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.repository.EntitlementDefRepository;
import com.xuejiai.aaf.module.billing.service.EntitlementService;
import com.xuejiai.aaf.module.billing.vo.EntitlementQuotaVO;
import com.xuejiai.aaf.module.system.file.repository.FileRecordRepository;

import lombok.RequiredArgsConstructor;

/** 权益额度接口 */
@RestController
@RequestMapping("/api/billing/entitlement")
@RequiredArgsConstructor
public class EntitlementController {

    private final EntitlementService entitlementService;
    private final EntitlementDefRepository defRepository;
    private final OperatorContext operatorContext;
    private final FileRecordRepository fileRecordRepository;

    /** 查询用户所有权益额度 */
    @GetMapping("/quotas")
    public Result<List<EntitlementQuotaVO>> listQuotas(
            @RequestParam(required = false) Long userId) {
        var uid = ownerId(userId);
        var quotas = entitlementService.listUserQuotas(uid);
        // storage 已用量实时从 sys_file 累计（单位 GB，向下取整展示）
        long storageUsedBytes = fileRecordRepository.sumSizeByUploaderId(uid);
        long storageUsedGB = storageUsedBytes / (1024L * 1024 * 1024);
        var vos =
                quotas.stream()
                        .map(
                                q -> {
                                    var def = defRepository.findById(q.getEntId()).orElse(null);
                                    var code = def != null ? def.getCode() : null;
                                    var used = "storage".equals(code) ? storageUsedGB : q.getUsed();
                                    var remain =
                                            "storage".equals(code)
                                                    ? Math.max(0, q.getTotal() - storageUsedGB)
                                                    : q.getRemain();
                                    return new EntitlementQuotaVO(
                                            q.getId(),
                                            code,
                                            def != null ? def.getName() : null,
                                            def != null ? def.getType() : null,
                                            def != null ? def.getUnit() : null,
                                            q.getTotal(),
                                            used,
                                            remain,
                                            q.getNextResetAt());
                                })
                        .toList();
        return Result.success(vos);
    }

    /** 手动触发周期重置（供定时任务或管理后台调用） */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/reset-expired")
    public Result<Integer> resetExpired() {
        return Result.success(entitlementService.resetExpiredQuotas());
    }

    private Long ownerId(Long fallbackUserId) {
        return operatorContext.currentOwnerId().orElse(fallbackUserId);
    }
}
