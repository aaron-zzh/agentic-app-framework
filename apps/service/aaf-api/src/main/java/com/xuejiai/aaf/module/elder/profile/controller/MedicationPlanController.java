package com.xuejiai.aaf.module.elder.profile.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationPlan;
import com.xuejiai.aaf.module.elder.profile.service.MedicationPlanService;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanActionDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanUpdateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 用药计划接口；标准 CRUD 外提供管理端启停动作。 */
@Tag(name = "养老-用药计划")
@RestController
@RequestMapping("/api/elder/profile/medication-plans")
@RequiredArgsConstructor
public class MedicationPlanController
        extends BaseCrudController<
                MedicationPlan,
                MedicationPlanVO,
                MedicationPlanCreateDTO,
                MedicationPlanUpdateDTO,
                MedicationPlanPageDTO> {

    private final MedicationPlanService service;

    @Override
    protected MedicationPlanService getService() {
        return service;
    }

    @Operation(summary = "启用用药计划")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/actions/activate")
    public Result<MedicationPlanVO> activate(@Valid @RequestBody MedicationPlanActionDTO command) {
        return Result.success(service.activate(command.id()));
    }

    @Operation(summary = "停用用药计划")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/actions/deactivate")
    public Result<MedicationPlanVO> deactivate(
            @Valid @RequestBody MedicationPlanActionDTO command) {
        return Result.success(service.deactivate(command.id()));
    }
}
