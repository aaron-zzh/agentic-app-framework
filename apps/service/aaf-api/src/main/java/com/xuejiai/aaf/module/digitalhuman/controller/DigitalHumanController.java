package com.xuejiai.aaf.module.digitalhuman.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.digitalhuman.domain.DigitalHuman;
import com.xuejiai.aaf.module.digitalhuman.service.DigitalHumanService;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanChangeVoiceDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanCreateDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanLinkAssistantDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanPageDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanUpdateDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 数字人接口；标准增删改查由 {@link BaseCrudController} 提供，以下为具名命令。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "数字人管理")
@RestController
@RequestMapping("/api/digital-humans")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DigitalHumanController
        extends BaseCrudController<
                DigitalHuman,
                DigitalHumanVO,
                DigitalHumanCreateDTO,
                DigitalHumanUpdateDTO,
                DigitalHumanPageDTO> {

    private final DigitalHumanService service;

    @Override
    protected DigitalHumanService getService() {
        return service;
    }

    @Operation(summary = "切换音色")
    @PreAuthorize("hasAuthority('digital-human:digital-human:change-voice')")
    @PostMapping("/{id}/change-voice")
    public Result<DigitalHumanVO> changeVoice(
            @PathVariable Long id, @Valid @RequestBody DigitalHumanChangeVoiceDTO command) {
        return Result.success(service.changeVoice(id, command));
    }

    @Operation(summary = "关联助理")
    @PreAuthorize("hasAuthority('digital-human:digital-human:link-assistant')")
    @PostMapping("/{id}/link-assistant")
    public Result<DigitalHumanVO> linkAssistant(
            @PathVariable Long id, @Valid @RequestBody DigitalHumanLinkAssistantDTO command) {
        return Result.success(service.linkAssistant(id, command));
    }

    @Operation(summary = "取消关联助理")
    @PreAuthorize("hasAuthority('digital-human:digital-human:link-assistant')")
    @DeleteMapping("/{id}/link-assistant")
    public Result<DigitalHumanVO> unlinkAssistant(@PathVariable Long id) {
        return Result.success(service.unlinkAssistant(id));
    }

    @Operation(summary = "归档数字人")
    @PreAuthorize("hasAuthority('digital-human:digital-human:archive')")
    @PostMapping("/{id}/archive")
    public Result<DigitalHumanVO> archive(@PathVariable Long id) {
        return Result.success(service.archive(id));
    }

    @Operation(summary = "恢复数字人")
    @PreAuthorize("hasAuthority('digital-human:digital-human:restore')")
    @PostMapping("/{id}/restore")
    public Result<DigitalHumanVO> restore(@PathVariable Long id) {
        return Result.success(service.restore(id));
    }
}
