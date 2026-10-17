package com.xuejiai.aaf.module.elder.guard.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.elder.guard.domain.AlertHandling;
import com.xuejiai.aaf.module.elder.guard.service.AlertHandlingService;
import com.xuejiai.aaf.module.elder.guard.vo.AlertHandlingCreateDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertHandlingPageDTO;
import com.xuejiai.aaf.module.elder.guard.vo.AlertHandlingVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 预警处理审计查询接口；管理端写能力已关闭。 */
@Tag(name = "养老-预警处理记录")
@RestController
@RequestMapping("/api/elder/guard/alert-handlings")
@RequiredArgsConstructor
public class AlertHandlingController
        extends BaseCrudController<
                AlertHandling,
                AlertHandlingVO,
                AlertHandlingCreateDTO,
                Void,
                AlertHandlingPageDTO> {

    private final AlertHandlingService service;

    @Override
    protected AlertHandlingService getService() {
        return service;
    }
}
