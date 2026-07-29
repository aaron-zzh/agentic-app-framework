package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.service.ContentProjectService;
import com.xuejiai.aaf.module.content.vo.ContentProjectCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 内容项目接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "内容项目")
@RestController
@RequestMapping("/api/content/projects")
@RequiredArgsConstructor
public class ContentProjectController
        extends BaseCrudController<
                ContentProject,
                ContentProjectVO,
                ContentProjectCreateDTO,
                ContentProjectUpdateDTO,
                ContentProjectPageDTO> {

    private final ContentProjectService service;

    @Override
    protected ContentProjectService getService() {
        return service;
    }
}
