package com.xuejiai.aaf.module.billing.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.billing.domain.SubscriptionSku;
import com.xuejiai.aaf.module.billing.service.SubscriptionPlanSkuCrudService;
import com.xuejiai.aaf.module.billing.vo.SubscriptionSkuCreateDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionSkuPageParam;
import com.xuejiai.aaf.module.billing.vo.SubscriptionSkuUpdateDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionSkuVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "订阅 SKU 管理")
@RestController
@RequestMapping("/api/billing/subscription-skus")
@RequiredArgsConstructor
public class SubscriptionSkuController
        extends BaseCrudController<
                SubscriptionSku,
                SubscriptionSkuVO,
                SubscriptionSkuCreateDTO,
                SubscriptionSkuUpdateDTO,
                SubscriptionSkuPageParam> {

    private final SubscriptionPlanSkuCrudService skuCrudService;

    @Override
    protected BaseCrudService<
                    SubscriptionSku,
                    SubscriptionSkuVO,
                    SubscriptionSkuCreateDTO,
                    SubscriptionSkuUpdateDTO,
                    SubscriptionSkuPageParam>
            getService() {
        return skuCrudService;
    }
}
