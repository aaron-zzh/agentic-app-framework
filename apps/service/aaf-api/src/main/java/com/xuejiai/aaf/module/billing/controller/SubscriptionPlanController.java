package com.xuejiai.aaf.module.billing.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.service.SubscriptionPlanCrudService;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanCreateDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanPageParam;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanUpdateDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPlanVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "订阅套餐管理")
@RestController
@RequestMapping("/api/billing/subscription-plans")
@RequiredArgsConstructor
public class SubscriptionPlanController
        extends BaseCrudController<
                SubscriptionPlan,
                SubscriptionPlanVO,
                SubscriptionPlanCreateDTO,
                SubscriptionPlanUpdateDTO,
                SubscriptionPlanPageParam> {

    private final SubscriptionPlanCrudService subscriptionPlanCrudService;

    @Override
    protected BaseCrudService<
                    SubscriptionPlan,
                    SubscriptionPlanVO,
                    SubscriptionPlanCreateDTO,
                    SubscriptionPlanUpdateDTO,
                    SubscriptionPlanPageParam>
            getService() {
        return subscriptionPlanCrudService;
    }

    @Operation(summary = "客户可购买套餐目录")
    @GetMapping("/catalog")
    public Result<List<SubscriptionPlanVO>> catalog() {
        return Result.success(subscriptionPlanCrudService.catalog());
    }
}
