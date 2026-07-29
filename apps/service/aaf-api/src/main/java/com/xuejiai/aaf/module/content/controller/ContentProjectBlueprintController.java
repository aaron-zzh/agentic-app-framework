package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentProjectBlueprint;
import com.xuejiai.aaf.module.content.service.ContentProjectBlueprintService;
import com.xuejiai.aaf.module.content.vo.ContentBlueprintVO;
import com.xuejiai.aaf.module.content.vo.ContentProjectBlueprintCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectBlueprintPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectBlueprintUpdateDTO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 项目蓝图接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "项目蓝图")
@RestController
@RequestMapping("/api/content/blueprints")
@RequiredArgsConstructor
public class ContentProjectBlueprintController
        extends BaseCrudController<
                ContentProjectBlueprint,
                ContentBlueprintVO,
                ContentProjectBlueprintCreateDTO,
                ContentProjectBlueprintUpdateDTO,
                ContentProjectBlueprintPageDTO> {

    private final ContentProjectBlueprintService service;

    @Override
    protected ContentProjectBlueprintService getService() {
        return service;
    }
}
