package com.xuejiai.aaf.module.billing.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.billing.domain.Level;
import com.xuejiai.aaf.module.billing.service.LevelService;
import com.xuejiai.aaf.module.billing.vo.LevelCreateDTO;
import com.xuejiai.aaf.module.billing.vo.LevelPageParam;
import com.xuejiai.aaf.module.billing.vo.LevelUpdateDTO;
import com.xuejiai.aaf.module.billing.vo.LevelVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "会员等级管理")
@RestController
@RequestMapping("/api/billing/levels")
@RequiredArgsConstructor
public class LevelController
        extends BaseCrudController<Level, LevelVO, LevelCreateDTO, LevelUpdateDTO, LevelPageParam> {

    private final LevelService levelService;

    @Override
    protected BaseCrudService<Level, LevelVO, LevelCreateDTO, LevelUpdateDTO, LevelPageParam>
            getService() {
        return levelService;
    }
}
