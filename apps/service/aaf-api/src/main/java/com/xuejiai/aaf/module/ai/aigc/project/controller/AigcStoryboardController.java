package com.xuejiai.aaf.module.ai.aigc.project.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcStoryboard;
import com.xuejiai.aaf.module.ai.aigc.project.service.AigcStoryboardService;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcStoryboardCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcStoryboardPageDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcStoryboardUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcStoryboardVO;
import com.xuejiai.aaf.module.ai.aigc.project.vo.AigcTimelineVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** AIGC 分镜规划接口。 */
@Tag(name = "AIGC 分镜规划")
@RestController
@RequestMapping("/api/aigc/storyboards")
@RequiredArgsConstructor
public class AigcStoryboardController
        extends BaseCrudController<
                AigcStoryboard,
                AigcStoryboardVO,
                AigcStoryboardCreateDTO,
                AigcStoryboardUpdateDTO,
                AigcStoryboardPageDTO> {

    private final AigcStoryboardService service;
    private final OperatorContext operatorContext;

    @Override
    protected BaseCrudService<
                    AigcStoryboard,
                    AigcStoryboardVO,
                    AigcStoryboardCreateDTO,
                    AigcStoryboardUpdateDTO,
                    AigcStoryboardPageDTO>
            getService() {
        return service;
    }

    @Operation(summary = "分镜一键导入时间轴")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/import-to-timeline")
    public Result<AigcTimelineVO> importToTimeline(@PathVariable Long id) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(service.importToTimeline(id, userId));
    }
}
