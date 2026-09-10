package com.xuejiai.aaf.module.billing.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;
import com.xuejiai.aaf.module.billing.service.SubscriptionRecordCrudService;
import com.xuejiai.aaf.module.billing.vo.CompensationResolutionDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionRecordPageParam;
import com.xuejiai.aaf.module.billing.vo.SubscriptionRecordVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "订阅购买流水")
@RestController
@RequestMapping("/api/billing/subscription-records")
@RequiredArgsConstructor
public class SubscriptionRecordController
        extends BaseCrudController<
                SubscriptionRecord, SubscriptionRecordVO, Void, Void, SubscriptionRecordPageParam> {

    private final SubscriptionRecordCrudService recordCrudService;

    @Override
    protected BaseCrudService<
                    SubscriptionRecord,
                    SubscriptionRecordVO,
                    Void,
                    Void,
                    SubscriptionRecordPageParam>
            getService() {
        return recordCrudService;
    }

    @Operation(summary = "记录订阅异常付款的人工处理结果")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/{id}/actions/resolve-compensation")
    public Result<SubscriptionRecordVO> resolveCompensation(
            @PathVariable Long id, @Valid @RequestBody CompensationResolutionDTO dto) {
        return Result.success(recordCrudService.resolveCompensation(id, dto));
    }
}
