package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;
import com.xuejiai.aaf.module.content.service.ContentProjectObjectService;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 项目对象接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "项目对象")
@RestController
@RequestMapping("/api/content/project-objects")
@RequiredArgsConstructor
public class ContentProjectObjectController
        extends BaseCrudController<
                ContentProjectObject,
                ContentProjectObjectVO,
                ContentProjectObjectCreateDTO,
                ContentProjectObjectUpdateDTO,
                ContentProjectObjectPageDTO> {

    private final ContentProjectObjectService service;

    @Override
    protected ContentProjectObjectService getService() {
        return service;
    }
}
