package com.xuejiai.aaf.module.content.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;
import com.xuejiai.aaf.module.content.service.ContentObjectVersionService;
import com.xuejiai.aaf.module.content.service.ContentProjectObjectService;
import com.xuejiai.aaf.module.content.vo.ContentObjectVersionVO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectCreateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectPageDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectUpdateDTO;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectVO;

import io.swagger.v3.oas.annotations.Operation;
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
    private final ContentObjectVersionService versionService;

    @Override
    protected ContentProjectObjectService getService() {
        return service;
    }

    @Operation(summary = "获取项目对象版本")
    @PreAuthorize("hasPermission(null, 'content:project-object:read')")
    @GetMapping("/{id}/versions")
    public Result<java.util.List<ContentObjectVersionVO>> versions(@PathVariable Long id) {
        return Result.success(versionService.listByObject(id));
    }

    @Operation(summary = "采用候选对象版本")
    @PreAuthorize("hasPermission(null, 'content:project-object:update')")
    @PostMapping("/{id}/versions/{versionId}/_adopt")
    public Result<ContentProjectObjectVO> adopt(
            @PathVariable Long id, @PathVariable Long versionId) {
        return Result.success(versionService.adopt(id, versionId));
    }

    @Operation(summary = "否决候选对象版本")
    @PreAuthorize("hasPermission(null, 'content:project-object:update')")
    @PostMapping("/{id}/versions/{versionId}/_reject")
    public Result<ContentObjectVersionVO> reject(
            @PathVariable Long id, @PathVariable Long versionId) {
        return Result.success(versionService.reject(id, versionId));
    }
}
