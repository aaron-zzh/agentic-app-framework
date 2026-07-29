package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentProjectRelation;
import com.xuejiai.aaf.module.content.service.ContentProjectRelationService;
import com.xuejiai.aaf.module.content.vo.ContentProjectRelationCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectRelationPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectRelationUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectRelationVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 项目关系接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "项目关系")
@RestController
@RequestMapping("/api/content/project-relations")
@RequiredArgsConstructor
public class ContentProjectRelationController
        extends BaseCrudController<
                ContentProjectRelation,
                ContentProjectRelationVO,
                ContentProjectRelationCreateDTO,
                ContentProjectRelationUpdateDTO,
                ContentProjectRelationPageDTO> {

    private final ContentProjectRelationService service;

    @Override
    protected ContentProjectRelationService getService() {
        return service;
    }
}
