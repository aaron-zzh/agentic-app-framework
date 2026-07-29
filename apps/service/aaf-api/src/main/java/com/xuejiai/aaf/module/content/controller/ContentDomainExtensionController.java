package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentDomainExtension;
import com.xuejiai.aaf.module.content.service.ContentDomainExtensionService;
import com.xuejiai.aaf.module.content.vo.ContentDomainExtensionCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentDomainExtensionPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentDomainExtensionUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentDomainExtensionVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 行业扩展接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "行业扩展")
@RestController
@RequestMapping("/api/content/domain-extensions")
@RequiredArgsConstructor
public class ContentDomainExtensionController
        extends BaseCrudController<
                ContentDomainExtension,
                ContentDomainExtensionVO,
                ContentDomainExtensionCreateDTO,
                ContentDomainExtensionUpdateDTO,
                ContentDomainExtensionPageDTO> {

    private final ContentDomainExtensionService service;

    @Override
    protected ContentDomainExtensionService getService() {
        return service;
    }
}
