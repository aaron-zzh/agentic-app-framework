package com.xuejiai.aaf.module.ai.aigc.video.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.aigc.video.domain.VideoTemplate;
import com.xuejiai.aaf.module.ai.aigc.video.service.VideoTemplateService;
import com.xuejiai.aaf.module.ai.aigc.video.vo.VideoTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.video.vo.VideoTemplatePageDTO;
import com.xuejiai.aaf.module.ai.aigc.video.vo.VideoTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.video.vo.VideoTemplateVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;

/**
 * AIGC 视频模板接口。
 *
 * @author AaronZZH & Kiro
 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 视频模板")
@RestController
@RequestMapping("/api/aigc/video/templates")
@RequiredArgsConstructor
public class VideoTemplateController
        extends BaseCrudController<
                VideoTemplate,
                VideoTemplateVO,
                VideoTemplateCreateDTO,
                VideoTemplateUpdateDTO,
                VideoTemplatePageDTO> {

    private final VideoTemplateService templateService;

    @Override
    protected VideoTemplateService getService() {
        return templateService;
    }
}
