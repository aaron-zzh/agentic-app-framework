package com.xuejiai.aaf.module.ai.aigc.studio.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskStatusEnum;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.media.service.AigcMediaService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.AigcMediaPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcProjectService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcProjectPageDTO;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskPageDTO;
import com.xuejiai.aaf.module.ai.aigc.work.service.AigcWorkService;
import com.xuejiai.aaf.module.ai.aigc.work.vo.AigcWorkPageDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Studio 首页生产概况聚合接口。 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC Studio")
@RestController
@RequestMapping("/api/aigc/studio")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcStudioHomeController {

    private final AigcProjectService projectService;
    private final AigcTaskService taskService;
    private final AigcMediaService mediaService;
    private final AigcWorkService workService;

    @Operation(summary = "获取 Studio 首页生产概况")
    @GetMapping("/home-summary")
    public Result<HomeSummaryVO> homeSummary() {
        var activeProjectCount =
                countProjects(AigcProjectLifecycle.CREATING)
                        + countProjects(AigcProjectLifecycle.EXECUTING)
                        + countProjects(AigcProjectLifecycle.ADOPTING);
        var activeTaskCount =
                countTasks(AigcTaskStatusEnum.PENDING) + countTasks(AigcTaskStatusEnum.RUNNING);
        return Result.success(
                new HomeSummaryVO(
                        activeProjectCount,
                        activeTaskCount,
                        countTasks(AigcTaskStatusEnum.FAIL),
                        mediaService.page(oneItem(new AigcMediaPageDTO())).total(),
                        workService.page(oneItem(new AigcWorkPageDTO())).total()));
    }

    private long countProjects(AigcProjectLifecycle status) {
        var request = oneItem(new AigcProjectPageDTO());
        request.setStatus(status);
        return projectService.page(request).total();
    }

    private long countTasks(AigcTaskStatusEnum status) {
        var request = oneItem(new AigcTaskPageDTO());
        request.setStatus(status.getCode());
        return taskService.page(request).total();
    }

    private static <P extends PageParam> P oneItem(P request) {
        request.setPageNo(1);
        request.setPageSize(1);
        return request;
    }

    public record HomeSummaryVO(
            long activeProjectCount,
            long activeTaskCount,
            long failedTaskCount,
            long mediaCount,
            long workCount) {}
}
