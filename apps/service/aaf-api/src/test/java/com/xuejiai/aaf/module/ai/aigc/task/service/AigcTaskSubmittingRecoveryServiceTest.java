package com.xuejiai.aaf.module.ai.aigc.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.event.AigcTaskTerminalEvent;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcTaskSubmittingRecoveryServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcTaskRepository repository;
    @Mock private AigcTaskProviderCapabilities providerCapabilities;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private AigcTaskSubmittingRecoveryService service;

    @Test
    @DisplayName("Given SUBMITTING 在 provider 调用窗口崩溃 When leaseUntil 早于 now Then 终态化并保留对账证据")
    void should_reconcile_without_resubmitting_when_submitting_crashes() {
        // 准备参数
        var cutoff = LocalDateTime.of(2026, 9, 5, 17, 30);
        var task = new AigcTask();
        task.setId(11L);
        task.setExecutionRunId(31L);
        task.setStatus("SUBMITTING");
        task.setProvider("dashscope");
        task.setProviderKey("task-stable-key");
        task.setSubmitOwner("worker-a");
        task.setSubmitLeaseUntil(cutoff.minusSeconds(1));
        task.setProviderResult("{\"request\":\"accepted-or-unknown\"}");
        when(repository.findLockedById(11L)).thenReturn(Optional.of(task));
        when(providerCapabilities.require("dashscope"))
                .thenReturn(new AigcTaskProviderCapabilities.Capabilities(false, false));

        // 调用
        var changed = service.markNeedsReconciliation(11L, cutoff);

        // 断言
        assertThat(changed).isTrue();
        assertThat(task.getStatus()).isEqualTo("NEEDS_RECONCILIATION");
        assertThat(task.getProviderResult())
                .contains("task-stable-key", "accepted-or-unknown", "NEEDS_RECONCILIATION");
        verify(repository).saveAndFlush(task);
        verify(eventPublisher)
                .publishEvent(org.mockito.ArgumentMatchers.any(AigcTaskTerminalEvent.class));
    }

    @Test
    @DisplayName("Given PREPARED 尚未调用 provider When recovery 扫描 Then 不进入 SUBMITTING 对账终态")
    void should_leave_prepared_for_safe_recovery() {
        // 准备参数
        var cutoff = LocalDateTime.of(2026, 9, 5, 17, 30);
        var task = new AigcTask();
        task.setId(12L);
        task.setStatus("PREPARED");
        when(repository.findLockedById(12L)).thenReturn(Optional.of(task));

        // 调用
        var changed = service.markNeedsReconciliation(12L, cutoff);

        // 断言
        assertThat(changed).isFalse();
        assertThat(task.getStatus()).isEqualTo("PREPARED");
        verify(repository, never()).saveAndFlush(task);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
