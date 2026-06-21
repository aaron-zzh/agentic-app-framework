package com.xuejiai.aaf.module.ai.aigc.avatar.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.avatar.domain.AiDigitalAvatar;
import com.xuejiai.aaf.module.ai.aigc.avatar.service.AiDigitalAvatarService;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarPageDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.avatar.vo.AiDigitalAvatarVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 数字人形象管理接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "数字人形象管理")
@RestController
@RequestMapping("/api/aigc/avatars")
@RequiredArgsConstructor
public class AiDigitalAvatarController
        extends BaseCrudController<
                AiDigitalAvatar,
                AiDigitalAvatarVO,
                AiDigitalAvatarCreateDTO,
                AiDigitalAvatarUpdateDTO,
                AiDigitalAvatarPageDTO> {

    private final AiDigitalAvatarService service;

    @Override
    protected BaseCrudService<
                    AiDigitalAvatar,
                    AiDigitalAvatarVO,
                    AiDigitalAvatarCreateDTO,
                    AiDigitalAvatarUpdateDTO,
                    AiDigitalAvatarPageDTO>
            getService() {
        return service;
    }
}
