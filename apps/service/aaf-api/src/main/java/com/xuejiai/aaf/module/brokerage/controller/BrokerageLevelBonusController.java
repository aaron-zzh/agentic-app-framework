package com.xuejiai.aaf.module.brokerage.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageLevelBonus;
import com.xuejiai.aaf.module.brokerage.service.BrokerageLevelBonusCrudService;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageLevelBonusDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageLevelBonusPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageLevelBonusVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 会员等级佣金加成管理接口。 */
@Tag(name = "会员等级佣金加成管理")
@RestController
@RequestMapping("/api/brokerage/level-bonuses")
@RequiredArgsConstructor
public class BrokerageLevelBonusController
        extends BaseCrudController<
                BrokerageLevelBonus,
                BrokerageLevelBonusVO,
                BrokerageLevelBonusDTO,
                BrokerageLevelBonusDTO,
                BrokerageLevelBonusPageParam> {

    private final BrokerageLevelBonusCrudService brokerageLevelBonusCrudService;

    @Override
    protected BrokerageLevelBonusCrudService getService() {
        return brokerageLevelBonusCrudService;
    }
}
