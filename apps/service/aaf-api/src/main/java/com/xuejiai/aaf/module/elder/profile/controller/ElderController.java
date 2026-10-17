package com.xuejiai.aaf.module.elder.profile.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.elder.profile.domain.Elder;
import com.xuejiai.aaf.module.elder.profile.service.ElderService;
import com.xuejiai.aaf.module.elder.profile.vo.ElderCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderUpdateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 长者档案接口。 */
@Tag(name = "养老-长者档案")
@RestController
@RequestMapping("/api/elder/profile/elders")
@RequiredArgsConstructor
public class ElderController
        extends BaseCrudController<Elder, ElderVO, ElderCreateDTO, ElderUpdateDTO, ElderPageDTO> {

    private final ElderService elderService;

    @Override
    protected BaseCrudService<Elder, ElderVO, ElderCreateDTO, ElderUpdateDTO, ElderPageDTO>
            getService() {
        return elderService;
    }
}
