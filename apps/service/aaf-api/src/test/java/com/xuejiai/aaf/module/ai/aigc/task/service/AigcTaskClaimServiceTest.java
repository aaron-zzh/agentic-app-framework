package com.xuejiai.aaf.module.ai.aigc.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcTaskClaimServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcTaskRepository repository;
    @InjectMocks private AigcTaskClaimService service;

    @Test
    @DisplayName("Given PREPARED intent When CAS owner 成功 Then 仅返回该 owner 的 SUBMITTING task")
    void should_return_intent_only_to_cas_owner() {
        // 准备参数
        var task = new AigcTask();
        task.setId(11L);
        task.setStatus("SUBMITTING");
        task.setSubmitOwner("owner-a");
        when(repository.claimIntent(any(), any(), any())).thenReturn(1);
        when(repository.findById(11L)).thenReturn(Optional.of(task));

        // 调用
        var claimed = service.claimIntent(11L, "owner-a");

        // 断言
        assertThat(claimed).contains(task);
    }

    @Test
    @DisplayName("Given intent 已被其他 owner 认领 When CAS 失败 Then 不读取并不提交 provider")
    void should_not_read_intent_when_cas_owner_loses() {
        // mock 方法
        when(repository.claimIntent(any(), any(), any())).thenReturn(0);

        // 调用 + 断言
        assertThat(service.claimIntent(11L, "owner-b")).isEmpty();
        verify(repository, never()).findById(11L);
    }

    @Test
    @DisplayName("Given 视频完成回调并发到达 When CAS completion Then 仅认领成功的 owner 获得任务")
    void should_return_completion_only_to_cas_winner() {
        // 准备参数
        var task = new AigcTask();
        task.setId(12L);
        task.setType("VIDEO");
        task.setStatus("COMPLETING");
        when(repository.claimCompletion("provider-12", "VIDEO")).thenReturn(1);
        when(repository.findByProviderTaskId("provider-12")).thenReturn(Optional.of(task));

        // 调用
        var claimed = service.claimCompletion("provider-12", "VIDEO");

        // 断言
        assertThat(claimed).contains(task);
    }

    @Test
    @DisplayName("Given 完成回调已被认领 When CAS completion 失败 Then 不再读取任务")
    void should_not_read_completion_when_cas_loses() {
        // mock 方法
        when(repository.claimCompletion("provider-12", "VIDEO")).thenReturn(0);

        // 调用 + 断言
        assertThat(service.claimCompletion("provider-12", "VIDEO")).isEmpty();
        verify(repository, never()).findByProviderTaskId("provider-12");
    }
}
