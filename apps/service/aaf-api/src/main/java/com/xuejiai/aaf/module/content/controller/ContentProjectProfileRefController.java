package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentProjectProfileRef;
import com.xuejiai.aaf.module.content.service.ContentProjectProfileRefService;
import com.xuejiai.aaf.module.content.vo.ContentProjectProfileRefCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectProfileRefPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectProfileRefUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectProfileRefVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 项目资料引用接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "项目资料引用")
@RestController
@RequestMapping("/api/content/project-profile-refs")
@RequiredArgsConstructor
public class ContentProjectProfileRefController
        extends BaseCrudController<
                ContentProjectProfileRef,
                ContentProjectProfileRefVO,
                ContentProjectProfileRefCreateDTO,
                ContentProjectProfileRefUpdateDTO,
                ContentProjectProfileRefPageDTO> {

    private final ContentProjectProfileRefService service;

    @Override
    protected ContentProjectProfileRefService getService() {
        return service;
    }
}
