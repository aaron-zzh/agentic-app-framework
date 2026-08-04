package com.xuejiai.aaf.module.ai.aigc.configuration.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcSnippet;
import com.xuejiai.aaf.module.ai.aigc.configuration.service.AigcSnippetService;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetPageDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcSnippetVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** AIGC 创作片段接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 创作片段")
@RestController
@RequestMapping("/api/aigc/snippets")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcSnippetController
        extends BaseCrudController<
                AigcSnippet,
                AigcSnippetVO,
                AigcSnippetCreateDTO,
                AigcSnippetUpdateDTO,
                AigcSnippetPageDTO> {

    private final AigcSnippetService service;

    @Override
    protected AigcSnippetService getService() {
        return service;
    }
}
