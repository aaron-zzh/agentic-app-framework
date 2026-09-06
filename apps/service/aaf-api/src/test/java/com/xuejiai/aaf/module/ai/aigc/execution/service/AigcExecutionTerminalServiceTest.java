package com.xuejiai.aaf.module.ai.aigc.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionSubmission;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionRunRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionSubmissionRepository;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectExecutionReservationReleaseCommand;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcExecutionTerminalServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcExecutionRunRepository runRepository;
    @Mock private AigcExecutionSubmissionRepository submissionRepository;
    @Mock private AigcProjectApi projectApi;
    @Mock private AigcActivityEventService activityEventService;

    private AigcExecutionTerminalService service;

    @BeforeEach
    void setUp() {
        service =
                new AigcExecutionTerminalService(
                        runRepository, submissionRepository, projectApi, activityEventService);
    }

    @Test
    @DisplayName("Given 子 Run 仍活动 When 汇总终态 Then 不释放 reservation")
    void should_not_release_reservation_when_descendant_is_active() {
        // 准备参数
        var root = run(1L, AigcExecutionRunStatus.RUNNING, "ORCHESTRATION");
        var succeeded = run(2L, AigcExecutionRunStatus.SUCCEEDED, "ACTIVITY");
        var active = run(3L, AigcExecutionRunStatus.RUNNING, "ACTIVITY");
        when(runRepository.findLockedById(1L)).thenReturn(Optional.of(root));
        when(runRepository.findByRootExecutionRunIdOrderByIdAsc(1L))
                .thenReturn(List.of(root, succeeded, active));

        // 调用
        service.onRunTerminal(succeeded);

        // 断言
        verify(projectApi, never())
                .releaseExecution(any(AigcProjectExecutionReservationReleaseCommand.class));
    }

    @Test
    @DisplayName("Given 成败混合且整树终态 When 汇总 Then root 为 PARTIALLY_SUCCEEDED 并释放")
    void should_mark_partial_and_release_when_all_descendants_are_terminal() {
        // 准备参数
        var root = run(1L, AigcExecutionRunStatus.RUNNING, "ORCHESTRATION");
        var succeeded = run(2L, AigcExecutionRunStatus.SUCCEEDED, "ACTIVITY");
        var failed = run(3L, AigcExecutionRunStatus.FAILED, "ACTIVITY");
        var submission = new AigcExecutionSubmission();
        submission.setId(10L);
        submission.setStatus("BOUND");
        when(runRepository.findLockedById(1L)).thenReturn(Optional.of(root));
        when(runRepository.findByRootExecutionRunIdOrderByIdAsc(1L))
                .thenReturn(List.of(root, succeeded, failed));
        when(submissionRepository.findLockedById(10L)).thenReturn(Optional.of(submission));

        // 调用
        service.onRunTerminal(failed);

        // 断言
        assertThat(root.getStatus()).isEqualTo(AigcExecutionRunStatus.PARTIALLY_SUCCEEDED);
        assertThat(submission.getStatus()).isEqualTo("TERMINAL");
        verify(projectApi)
                .releaseExecution(any(AigcProjectExecutionReservationReleaseCommand.class));
    }

    private AigcExecutionRun run(Long id, AigcExecutionRunStatus status, String runKind) {
        var run = new AigcExecutionRun();
        run.setId(id);
        run.setRootExecutionRunId(1L);
        run.setProjectId(20L);
        run.setExecutionSubmissionId(10L);
        run.setExecutionReservationId(30L);
        run.setOwnerId(40L);
        run.setRunKind(runKind);
        run.setStatus(status);
        run.setVersion(1);
        return run;
    }
}
