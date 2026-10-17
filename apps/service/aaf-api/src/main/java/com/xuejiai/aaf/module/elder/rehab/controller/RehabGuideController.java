package com.xuejiai.aaf.module.elder.rehab.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.elder.rehab.domain.RehabGuide;
import com.xuejiai.aaf.module.elder.rehab.service.RehabGuideService;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuideActionDTO;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuideCreateDTO;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuidePageDTO;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuideUpdateDTO;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuideVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 康复指导管理接口。 */
@Tag(name = "养老-康复指导")
@RestController
@RequestMapping("/api/elder/rehab/guides")
@RequiredArgsConstructor
public class RehabGuideController
        extends BaseCrudController<
                RehabGuide,
                RehabGuideVO,
                RehabGuideCreateDTO,
                RehabGuideUpdateDTO,
                RehabGuidePageDTO> {

    private final RehabGuideService service;

    @Override
    protected RehabGuideService getService() {
        return service;
    }

    @Operation(summary = "发布康复指导")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/actions/publish")
    public Result<RehabGuideVO> publish(@Valid @RequestBody RehabGuideActionDTO command) {
        return Result.success(service.publish(command));
    }

    @Operation(summary = "下线康复指导")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/actions/offline")
    public Result<RehabGuideVO> offline(@Valid @RequestBody RehabGuideActionDTO command) {
        return Result.success(service.offline(command));
    }
}
