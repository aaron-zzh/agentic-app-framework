package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentProjectType;
import com.xuejiai.aaf.module.content.service.ContentProjectTypeService;
import com.xuejiai.aaf.module.content.vo.ContentProjectTypeCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectTypePageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectTypeUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectTypeVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 项目类型接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "项目类型")
@RestController
@RequestMapping("/api/content/project-types")
@RequiredArgsConstructor
public class ContentProjectTypeController
        extends BaseCrudController<
                ContentProjectType,
                ContentProjectTypeVO,
                ContentProjectTypeCreateDTO,
                ContentProjectTypeUpdateDTO,
                ContentProjectTypePageDTO> {

    private final ContentProjectTypeService service;

    @Override
    protected ContentProjectTypeService getService() {
        return service;
    }
}
