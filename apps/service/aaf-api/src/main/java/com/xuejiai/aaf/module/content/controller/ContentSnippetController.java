package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentSnippet;
import com.xuejiai.aaf.module.content.service.ContentSnippetService;
import com.xuejiai.aaf.module.content.vo.ContentSnippetCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentSnippetPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentSnippetUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentSnippetVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 创作片段接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "创作片段")
@RestController
@RequestMapping("/api/content/snippets")
@RequiredArgsConstructor
public class ContentSnippetController
        extends BaseCrudController<
                ContentSnippet,
                ContentSnippetVO,
                ContentSnippetCreateDTO,
                ContentSnippetUpdateDTO,
                ContentSnippetPageDTO> {

    private final ContentSnippetService service;

    @Override
    protected ContentSnippetService getService() {
        return service;
    }
}
