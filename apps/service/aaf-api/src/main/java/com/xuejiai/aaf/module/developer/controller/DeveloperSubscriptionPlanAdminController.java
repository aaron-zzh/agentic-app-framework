package com.xuejiai.aaf.module.developer.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseOwnerRequired;
import com.xuejiai.aaf.framework.security.license.PremiumRequired;
import com.xuejiai.aaf.module.developer.domain.DeveloperSubscriptionPlan;
import com.xuejiai.aaf.module.developer.service.DeveloperSubscriptionPlanCrudService;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionPlanCreateDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionPlanPageParam;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionPlanUpdateDTO;
import com.xuejiai.aaf.module.developer.vo.DeveloperSubscriptionPlanVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 开发者订阅套餐管理接口。 */
@Tag(name = "开发者订阅套餐管理")
@RestController
@RequestMapping("/api/developer/admin/subscription-plans")
@RequiredArgsConstructor
@PremiumRequired("开发者订阅套餐管理")
@FeatureRequired("developer")
@LicenseOwnerRequired("官方开发者订阅套餐管理")
public class DeveloperSubscriptionPlanAdminController
        extends BaseCrudController<
                DeveloperSubscriptionPlan,
                DeveloperSubscriptionPlanVO,
                DeveloperSubscriptionPlanCreateDTO,
                DeveloperSubscriptionPlanUpdateDTO,
                DeveloperSubscriptionPlanPageParam> {

    private final DeveloperSubscriptionPlanCrudService planService;

    @Override
    protected DeveloperSubscriptionPlanCrudService getService() {
        return planService;
    }
}
