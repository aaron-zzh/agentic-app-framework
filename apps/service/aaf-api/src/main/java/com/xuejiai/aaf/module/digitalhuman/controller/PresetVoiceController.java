package com.xuejiai.aaf.module.digitalhuman.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.digitalhuman.service.PresetVoiceService;
import com.xuejiai.aaf.module.digitalhuman.vo.PresetVoiceVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "预设音色管理")
@RestController
@RequestMapping("/api/preset-voices")
@RequiredArgsConstructor
public class PresetVoiceController {

    private final PresetVoiceService presetVoiceService;

    @Operation(summary = "获取启用的预设音色列表")
    @GetMapping
    public Result<List<PresetVoiceVO>> listActive() {
        return Result.success(presetVoiceService.listActive());
    }

    @Operation(summary = "获取所有预设音色列表（含停用）")
    @GetMapping("/all")
    public Result<List<PresetVoiceVO>> listAll() {
        return Result.success(presetVoiceService.listAll());
    }
}
