package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentObjectVersion;
import com.xuejiai.aaf.module.content.service.ContentObjectVersionService;
import com.xuejiai.aaf.module.content.vo.ContentObjectVersionPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentObjectVersionVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 内容对象版本只读接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "内容对象版本")
@RestController
@RequestMapping("/api/content/object-versions")
@RequiredArgsConstructor
public class ContentObjectVersionController
        extends BaseCrudController<
                ContentObjectVersion,
                ContentObjectVersionVO,
                Void,
                Void,
                ContentObjectVersionPageDTO> {

    private final ContentObjectVersionService service;

    @Override
    protected ContentObjectVersionService getService() {
        return service;
    }
}
