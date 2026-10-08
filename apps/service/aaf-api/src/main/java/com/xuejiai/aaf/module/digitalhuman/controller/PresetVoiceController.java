package com.xuejiai.aaf.module.digitalhuman.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.digitalhuman.domain.PresetVoice;
import com.xuejiai.aaf.module.digitalhuman.service.PresetVoiceService;
import com.xuejiai.aaf.module.digitalhuman.vo.PresetVoicePageDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.PresetVoiceVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 数字人预设音色只读接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "预设音色")
@RestController
@RequestMapping("/api/preset-voices")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PresetVoiceController
        extends BaseCrudController<PresetVoice, PresetVoiceVO, Void, Void, PresetVoicePageDTO> {

    private final PresetVoiceService service;

    @Override
    protected PresetVoiceService getService() {
        return service;
    }
}
