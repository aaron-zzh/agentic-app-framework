package com.xuejiai.aaf.framework.task.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.engine.meta.runtime.AafTask;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskContext;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskRuntime;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class TaskHandlerRegistrarTest extends BaseMockitoUnitTest {

    @Mock private TaskHandler handler;
    @Mock private TaskRuntime taskRuntime;
    @Mock private TaskInboxExecutor taskInboxExecutor;

    @Test
    @DisplayName("Given Spring 中存在 TaskHandler When Bean 初始化 Then 注册适配任务并可执行 payload")
    void should_register_and_adapt_handler_when_initialized() throws Exception {
        // 准备参数
        when(handler.taskType()).thenReturn("TODO_CLEAR_DONE");
        when(handler.timeoutSeconds()).thenReturn(30L);
        when(taskInboxExecutor.execute(anyString(), anyString(), anyString(), any(Runnable.class)))
                .thenAnswer(
                        invocation -> {
                            invocation.getArgument(3, Runnable.class).run();
                            return true;
                        });
        var registrar =
                new TaskHandlerRegistrar(List.of(handler), taskRuntime, taskInboxExecutor);
        var captor = ArgumentCaptor.forClass(AafTask.class);

        // 调用
        registrar.afterPropertiesSet();
        verify(taskRuntime).register(captor.capture());
        var task = captor.getValue();
        var context = new TaskContext("execution-1", task.taskType(), "{\"id\":1}");
        context.setVariable(TaskContext.QUEUE_TASK_ID, "task-1");
        var result = task.execute(context);

        // 断言
        assertThat(task.taskType()).isEqualTo("TODO_CLEAR_DONE");
        assertThat(task.timeoutSeconds()).isEqualTo(30L);
        assertThat(result.success()).isTrue();
        verify(taskInboxExecutor)
                .execute(anyString(), anyString(), anyString(), any(Runnable.class));
        verify(handler).handle("task-1", "{\"id\":1}");
    }
}
