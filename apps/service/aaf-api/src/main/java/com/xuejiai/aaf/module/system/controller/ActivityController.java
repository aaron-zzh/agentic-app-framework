package com.xuejiai.aaf.module.system.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.system.service.ActivityService;
import com.xuejiai.aaf.module.system.service.ActivityService.ActivityTimelineItem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 活动流接口。 */
@Tag(name = "活动流")
@RestController
@RequestMapping("/api/{entity}/{id}/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "查询活动流（活动+评论混合时间线）")
    @GetMapping
    public Result<PageResult<ActivityTimelineItem>> timeline(
            @PathVariable("entity") String entity,
            @PathVariable("id") Long id,
            @Validated @ParameterObject PageParam pageParam) {
        return Result.success(activityService.timeline(entity, id, pageParam));
    }
}
