package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentBrandProfile;
import com.xuejiai.aaf.module.content.service.ContentBrandProfileService;
import com.xuejiai.aaf.module.content.vo.ContentBrandProfileCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentBrandProfilePageDTO;
import com.xuejiai.aaf.module.content.vo.ContentBrandProfileUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentBrandProfileVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 品牌/IP 资料接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "品牌/IP 资料")
@RestController
@RequestMapping("/api/content/brand-profiles")
@RequiredArgsConstructor
public class ContentBrandProfileController
        extends BaseCrudController<
                ContentBrandProfile,
                ContentBrandProfileVO,
                ContentBrandProfileCreateDTO,
                ContentBrandProfileUpdateDTO,
                ContentBrandProfilePageDTO> {

    private final ContentBrandProfileService service;

    @Override
    protected ContentBrandProfileService getService() {
        return service;
    }
}
