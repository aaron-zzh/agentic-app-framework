package com.xuejiai.aaf.framework.engine.meta.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.engine.bpmn.api.BpmnEngine;
import com.xuejiai.aaf.framework.task.TaskMonitor;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class TaskRuntimeInProgressTest extends BaseMockitoUnitTest {

    @Mock private TaskMonitor taskMonitor;
    @Mock private TaskNotifier taskNotifier;
    @Mock private BpmnEngine bpmnEngine;

    private TaskRuntime taskRuntime;

    @BeforeEach
    void setUp() {
        taskRuntime = new TaskRuntime(taskMonitor, taskNotifier, bpmnEngine);
        when(taskMonitor.recordStart(
                        anyString(), anyString(), any(), any(), any(), anyString()))
                .thenReturn(10L);
        taskRuntime.register(inProgressTask());
    }

    @AfterEach
    void tearDown() {
        taskRuntime.close();
    }

    @Test
    @DisplayName("Given 任务仍在执行 When 队列提交 Then 终结监控并原样传播控制异常")
    void should_propagate_in_progress_from_submit() {
        assertThatThrownBy(
                        () ->
                                taskRuntime.submit(
                                        "LONG_TASK", "{}", ExecutionMeta.queue((short) 5, "{}")))
                .isInstanceOf(TaskExecutionInProgressException.class)
                .hasMessage("still running");
        verify(taskMonitor).recordFailure(10L, "still running");
    }

    @Test
    @DisplayName("Given 任务仍在执行 When 带进度提交 Then 对称传播控制异常")
    void should_propagate_in_progress_from_progress_submit() {
        assertThatThrownBy(
                        () ->
                                taskRuntime.submitWithProgress(
                                        "LONG_TASK", "{}", 1, progress -> {}))
                .isInstanceOf(TaskExecutionInProgressException.class)
                .hasMessage("still running");
        verify(taskMonitor).recordFailure(10L, "still running");
    }

    private AafTask inProgressTask() {
        return new AafTask() {
            @Override
            public String taskType() {
                return "LONG_TASK";
            }

            @Override
            public TaskResult execute(TaskContext context) {
                throw new TaskExecutionInProgressException("still running");
            }
        };
    }
}
