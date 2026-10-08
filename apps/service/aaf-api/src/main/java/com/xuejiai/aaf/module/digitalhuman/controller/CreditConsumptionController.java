package com.xuejiai.aaf.module.digitalhuman.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.digitalhuman.domain.CreditConsumption;
import com.xuejiai.aaf.module.digitalhuman.service.CreditConsumptionService;
import com.xuejiai.aaf.module.digitalhuman.vo.CreditConsumptionPageDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.CreditConsumptionVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 数字人积分消耗记录只读接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "数字人积分消耗记录")
@RestController
@RequestMapping("/api/digital-human-credit-consumptions")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CreditConsumptionController
        extends BaseCrudController<
                CreditConsumption, CreditConsumptionVO, Void, Void, CreditConsumptionPageDTO> {

    private final CreditConsumptionService service;

    @Override
    protected CreditConsumptionService getService() {
        return service;
    }
}
