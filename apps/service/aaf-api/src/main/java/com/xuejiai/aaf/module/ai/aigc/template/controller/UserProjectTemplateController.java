package com.xuejiai.aaf.module.ai.aigc.template.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectVO;
import com.xuejiai.aaf.module.ai.aigc.template.domain.UserProjectTemplate;
import com.xuejiai.aaf.module.ai.aigc.template.service.UserProjectTemplateService;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplateCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplateForkDTO;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplatePageDTO;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplateUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.template.vo.UserProjectTemplateVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 项目模板接口。 */
@Tag(name = "项目模板")
@RestController
@RequestMapping("/api/aigc/project-templates")
@RequiredArgsConstructor
public class UserProjectTemplateController
        extends BaseCrudController<
                UserProjectTemplate,
                UserProjectTemplateVO,
                UserProjectTemplateCreateDTO,
                UserProjectTemplateUpdateDTO,
                UserProjectTemplatePageDTO> {

    private final UserProjectTemplateService service;

    @Override
    protected UserProjectTemplateService getService() {
        return service;
    }

    @Operation(summary = "Fork 模板创建项目")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/fork")
    public Result<AigcProjectVO> fork(
            @PathVariable Long id, @RequestBody(required = false) UserProjectTemplateForkDTO dto) {
        return Result.success(service.fork(id, dto));
    }
}
