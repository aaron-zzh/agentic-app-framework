package com.xuejiai.aaf.module.content.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_EXECUTION_RUN_NOT_FOUND;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_OBJECT_NOT_FOUND;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;
import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;
import com.xuejiai.aaf.module.content.repository.ContentExecutionRunRepository;
import com.xuejiai.aaf.module.content.repository.ContentProjectObjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * Content Studio 聚合访问校验；对象与执行记录统一继承所属项目的访问边界。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class ContentProjectAccessGuard {

    private final ContentProjectService projectService;
    private final ContentProjectObjectRepository objectRepository;
    private final ContentExecutionRunRepository runRepository;

    public ContentProject requireProject(Long projectId) {
        return projectService.requireAccessibleEntity(projectId);
    }

    public ContentProjectObject requireObject(Long objectId) {
        var object =
                objectRepository
                        .findById(objectId)
                        .orElseThrow(() -> exception(CONTENT_OBJECT_NOT_FOUND));
        requireProject(object.getProjectId());
        return object;
    }

    public ContentProjectObject requireLockedObject(Long objectId) {
        var object =
                objectRepository
                        .findLockedById(objectId)
                        .orElseThrow(() -> exception(CONTENT_OBJECT_NOT_FOUND));
        requireProject(object.getProjectId());
        return object;
    }

    public ContentExecutionRun requireRun(Long runId) {
        var run =
                runRepository
                        .findById(runId)
                        .orElseThrow(() -> exception(CONTENT_EXECUTION_RUN_NOT_FOUND));
        requireProject(run.getProjectId());
        return run;
    }
}
