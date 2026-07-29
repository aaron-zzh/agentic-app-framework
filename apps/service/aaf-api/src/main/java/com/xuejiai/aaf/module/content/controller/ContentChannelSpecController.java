package com.xuejiai.aaf.module.content.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentChannelSpec;
import com.xuejiai.aaf.module.content.service.ContentChannelSpecService;
import com.xuejiai.aaf.module.content.vo.ContentChannelSpecCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentChannelSpecPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentChannelSpecUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentChannelSpecVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 渠道规格接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "渠道规格")
@RestController
@RequestMapping("/api/content/channel-specs")
@RequiredArgsConstructor
public class ContentChannelSpecController
        extends BaseCrudController<
                ContentChannelSpec,
                ContentChannelSpecVO,
                ContentChannelSpecCreateDTO,
                ContentChannelSpecUpdateDTO,
                ContentChannelSpecPageDTO> {

    private final ContentChannelSpecService service;

    @Override
    protected ContentChannelSpecService getService() {
        return service;
    }
}
