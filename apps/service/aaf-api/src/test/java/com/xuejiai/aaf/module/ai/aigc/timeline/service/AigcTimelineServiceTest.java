package com.xuejiai.aaf.module.ai.aigc.timeline.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectLifecycle;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;
import com.xuejiai.aaf.module.ai.aigc.timeline.api.AigcTimelineCreateCommand;
import com.xuejiai.aaf.module.ai.aigc.timeline.repository.AigcStoryboardExportRepository;
import com.xuejiai.aaf.module.ai.aigc.timeline.repository.AigcTimelineClipRepository;
import com.xuejiai.aaf.module.ai.aigc.timeline.repository.AigcTimelineCompositionRepository;
import com.xuejiai.aaf.module.ai.aigc.timeline.repository.AigcTimelineTrackRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcTimelineServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcTimelineCompositionRepository repository;
    @Mock private AigcTimelineTrackRepository trackRepository;
    @Mock private AigcTimelineClipRepository clipRepository;
    @Mock private AigcStoryboardExportRepository storyboardExportRepository;
    @Mock private AigcProjectApi projectApi;
    @Mock private AigcMediaApi mediaApi;
    @Mock private OperatorContext operatorContext;
    @InjectMocks private AigcTimelineService service;

    @Test
    @DisplayName("Given expectedProjectVersion When 创建 Timeline Then 先通过 creative CAS 锁 Project")
    void should_lock_project_with_expected_version_before_create() {
        // 准备参数
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(7L));
        when(projectApi.lockForCreativeMutation(1L, 7L, 5)).thenReturn(project());
        var command =
                new AigcTimelineCreateCommand(
                        1L, 5, null, "主时间线", 0L, BigDecimal.valueOf(30), 1920, 1080);

        // 调用
        service.create(command);

        // 断言
        verify(projectApi).lockForCreativeMutation(1L, 7L, 5);
        verify(repository).save(org.mockito.ArgumentMatchers.any());
    }

    private AigcProjectView project() {
        return new AigcProjectView(
                1L,
                2L,
                3L,
                "视频项目",
                AigcProjectLifecycle.CREATING,
                5,
                null,
                "social",
                null,
                "standard",
                "manual",
                List.of(),
                List.of(),
                null,
                BigDecimal.ZERO,
                null,
                "项目简报",
                null,
                7L);
    }
}
